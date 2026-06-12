import os
import json
import logging
import base64
import requests
from typing import List, Optional
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from PIL import Image
import io

# Setup logger
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ocr-service")

app = FastAPI(title="FMCG Invoice OCR Scanner Service")

# Allow all CORS for simple microservice communication
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Read Gemini API Key
api_key = os.environ.get("GEMINI_API_KEY")
if not api_key:
    logger.warning("GEMINI_API_KEY environment variable is not set! Calls to Gemini will fail.")

class InvoiceItem(BaseModel):
    name: str
    mrp: float = 0.0
    batch_number: Optional[str] = None
    expiry_date: Optional[str] = None
    invoice_cases: int = 1
    packs_per_case: int = 1
    buy_price_per_piece: float = 0.0
    taxable_value: float = 0.0
    gst_percent: float = 5.0

class ScanResponse(BaseModel):
    rawItems: List[InvoiceItem]

PROMPT = """
You are an expert accountant parsing a retail shop purchase tax invoice.
Scan the uploaded image of the tax invoice and extract all listed items.

The invoice is a structured table with the following columns:
S No. | Item Name | Hsn Code | Batch Number | Expiry Date | MRP | UOM | Total Invoice Cases | Price/Piece | Net Amt. | GST Discount (%) | GST Discount | Total Scheme Dis. | Taxable Value | GST (%)

CRITICAL ALIGNMENT RULE:
You MUST read the table row-by-row. Do NOT mix up columns from different rows!
For each serial number (S No.), extract all of its columns from the same horizontal line.
For example, for a single row, the 'Item Name', 'Batch Number', 'Expiry Date', 'MRP', 'UOM', 'Total Invoice Cases', 'Price/Piece', and 'Taxable Value' must all belong to that same row. Do NOT align the 'Batch Number' of one row with the 'Item Name' of another row.

Please extract:
1. "name": The exact name of the item (from 'Item Name' column).
2. "mrp": The Maximum Retail Price per piece/packet (numeric float, from the 'MRP' column).
3. "batch_number": Read from the 'Batch Number' column.
   - WARNING: Do NOT confuse the numeric 'Hsn Code' (e.g. '21069099') with the alphanumeric 'Batch Number' (e.g. 'MAFE30', 'MAFE24').
4. "expiry_date": The expiry date from the 'Expiry Date' column. Parse it and return strictly in the format "YYYY-MM-DD" (if it is DD/MM/YY on the invoice, convert it, e.g., 27/09/26 -> 2026-09-27).
5. "invoice_cases": The quantity of cases purchased from the 'Total Invoice Cases' column.
6. "packs_per_case": The packing quantity per case from the 'UOM' column (integer, e.g., 100, 516, 312).
7. "buy_price_per_piece": The buy price per piece without tax. Read this strictly from the 'Price/Piece' column (numeric float).
8. "taxable_value": The total taxable value of the row after discount but before GST tax. Read this strictly from the 'Taxable Value' column (numeric float). If the 'Taxable Value' column is missing or empty, use the 'Net Amt' minus any discount, or raw row total before tax.
9. "gst_percent": The GST tax percentage applied to this item from the 'GST (%)' column (numeric float, e.g., 5.0, 12.0, 18.0).

Return ONLY the raw JSON list inside a code block. Do not write any markdown descriptions or introductory text.
"""



def compress_image(image_bytes: bytes) -> bytes:
    """Resize and compress image to speed up upload and API response times."""
    # If image is already very small (< 150KB), don't compress it
    if len(image_bytes) < 150 * 1024:
        logger.info("Image size is already under 150KB. Skipping compression.")
        return image_bytes
        
    try:
        img = Image.open(io.BytesIO(image_bytes))
        
        # Convert RGBA to RGB if necessary
        if img.mode in ('RGBA', 'LA') or (img.mode == 'P' and 'transparency' in img.info):
            img = img.convert('RGB')
            
        # Max dimensions for fast OCR processing while preserving text clarity
        max_size = (1800, 1800)
        img.thumbnail(max_size, Image.Resampling.LANCZOS)
        
        out_io = io.BytesIO()
        img.save(out_io, format="JPEG", quality=90)
        compressed = out_io.getvalue()
        logger.info(f"Compressed image from {len(image_bytes)} to {len(compressed)} bytes.")
        return compressed
    except Exception as e:
        logger.error(f"Error compressing image: {e}")
        return image_bytes

@app.get("/health")
def health_check():
    return {"status": "ok", "api_configured": api_key is not None}

@app.post("/ocr/scan-invoice", response_model=ScanResponse)
async def scan_invoice(file: UploadFile = File(...)):
    if not api_key:
        raise HTTPException(
            status_code=500,
            detail="GEMINI_API_KEY is not configured on the OCR service."
        )
        
    try:
        contents = await file.read()
        logger.info(f"Received file: {file.filename}, size: {len(contents)} bytes")
        
        # Compress image to speed up upload
        processed_image = compress_image(contents)
        logger.info(f"Processed file size for upload: {len(processed_image)} bytes")
        
        # Base64 encode for raw HTTP REST request
        base64_image = base64.b64encode(processed_image).decode('utf-8')
        
        # Using gemini-flash-lite-latest to ensure fast response times, high rate limits and API compatibility
        url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key={api_key}"
        
        payload = {
            "contents": [{
                "parts": [
                    {"text": PROMPT},
                    {
                        "inlineData": {
                            "mime_type": "image/jpeg",
                            "data": base64_image
                        }
                    }
                ]
            }]
        }
        
        logger.info("Calling Gemini REST API...")
        headers = {"Content-Type": "application/json"}
        response = requests.post(url, json=payload, headers=headers)
        
        if response.status_code != 200:
            logger.error(f"Gemini API returned error {response.status_code}: {response.text}")
            raise HTTPException(
                status_code=response.status_code,
                detail=f"Gemini API error: {response.text}"
            )
            
        response_json = response.json()
        
        try:
            response_text = response_json['candidates'][0]['content']['parts'][0]['text'].strip()
        except (KeyError, IndexError) as err:
            logger.error(f"Unexpected response structure: {response_json}, error: {err}")
            raise HTTPException(
                status_code=500,
                detail="Failed to parse text from Gemini response structure"
            )
        
        # Extract JSON from potential markdown code fences
        if "```json" in response_text:
            response_text = response_text.split("```json")[1].split("```")[0].strip()
        elif "```" in response_text:
            response_text = response_text.split("```")[1].split("```")[0].strip()
            
        parsed_json = json.loads(response_text)
        
        # Ensure it matches the ScanResponse model structure {"rawItems": [...]}
        if isinstance(parsed_json, list):
            return {"rawItems": parsed_json}
        elif isinstance(parsed_json, dict):
            if "rawItems" in parsed_json:
                return parsed_json
            # Find any list value inside the dict and use it
            for val in parsed_json.values():
                if isinstance(val, list):
                    return {"rawItems": val}
            return {"rawItems": []}
        else:
            return {"rawItems": []}
        
    except json.JSONDecodeError as jde:
        logger.error(f"JSON Decode Error parsing Gemini output: {response_text}, error: {jde}")
        raise HTTPException(
            status_code=500,
            detail=f"Failed to parse AI output into valid JSON. Raw output was: {response_text[:300]}"
        )
    except Exception as e:
        logger.error(f"Error processing invoice scanning: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

class TextGenerationRequest(BaseModel):
    prompt: str

class StructuredRequest(BaseModel):
    prompt: str
    systemInstruction: Optional[str] = None

@app.post("/ocr/generate-text")
async def generate_text(req: TextGenerationRequest):
    if not api_key:
        raise HTTPException(status_code=500, detail="GEMINI_API_KEY is not configured.")
    try:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key={api_key}"
        payload = {
            "contents": [{
                "parts": [{"text": req.prompt}]
            }]
        }
        headers = {"Content-Type": "application/json"}
        response = requests.post(url, json=payload, headers=headers)
        if response.status_code != 200:
            raise HTTPException(status_code=response.status_code, detail=response.text)
        
        response_json = response.json()
        try:
            text = response_json['candidates'][0]['content']['parts'][0]['text'].strip()
            return {"text": text}
        except (KeyError, IndexError):
            raise HTTPException(status_code=500, detail="Unexpected response structure from Gemini API")
    except Exception as e:
        logger.error(f"Error in generate_text: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/ocr/parse-structured")
async def parse_structured(req: StructuredRequest):
    if not api_key:
        raise HTTPException(status_code=500, detail="GEMINI_API_KEY is not configured.")
    try:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key={api_key}"
        payload = {
            "contents": [{
                "parts": [{"text": req.prompt}]
            }],
            "generationConfig": {
                "responseMimeType": "application/json"
            }
        }
        
        if req.systemInstruction:
            payload["systemInstruction"] = {
                "parts": [{"text": req.systemInstruction}]
            }
            
        headers = {"Content-Type": "application/json"}
        response = requests.post(url, json=payload, headers=headers)
        if response.status_code != 200:
            raise HTTPException(status_code=response.status_code, detail=response.text)
            
        response_json = response.json()
        try:
            response_text = response_json['candidates'][0]['content']['parts'][0]['text'].strip()
        except (KeyError, IndexError):
            raise HTTPException(status_code=500, detail="Unexpected response structure from Gemini API")
            
        # Clean JSON fences if present, though responseMimeType: application/json should return raw JSON
        if "```json" in response_text:
            response_text = response_text.split("```json")[1].split("```")[0].strip()
        elif "```" in response_text:
            response_text = response_text.split("```")[1].split("```")[0].strip()
            
        try:
            parsed_json = json.loads(response_text)
            return parsed_json
        except json.JSONDecodeError:
            return {"rawText": response_text}
            
    except Exception as e:
        logger.error(f"Error in parse_structured: {e}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    # Start server on port 8087
    uvicorn.run(app, host="127.0.0.1", port=8087)
