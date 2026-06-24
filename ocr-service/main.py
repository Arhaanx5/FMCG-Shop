import os
import json
import logging
import base64
import asyncio
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
    net_amount: float = 0.0           # Gross before discount
    cst_discount: float = 0.0         # CST/Scheme discount amount
    taxable_value: float = 0.0        # After discount, before GST
    gst_percent: float = 5.0
    offer_secondary_received: int = 0 # Free offer units detected

class ScanResponse(BaseModel):
    invoice_number: Optional[str] = None
    rawItems: List[InvoiceItem]

PROMPT = """
You are an expert accountant parsing a retail shop purchase tax invoice.
Scan the uploaded image of the tax invoice.

First, look at the header area of the invoice and extract:
- "invoice_number": Locate and extract the Invoice Number (or Bill Number / Inv No., e.g., '26-27/SA-0821', 'SA-0820'). If not found, return null.

Next, parse the listed invoice items. The invoice is a structured table with the following columns:
S No. | Item Name | Hsn Code | Batch Number | Expiry Date | MRP | UOM | Total Invoice Cases | Price/Piece | Net Amt. | GST Discount (%) | GST Discount | Total Scheme Dis. | Taxable Value | GST (%)

CRITICAL ALIGNMENT RULE:
You MUST read the table row-by-row. Do NOT mix up columns from different rows!
For each serial number (S No.), extract all of its columns from the same horizontal line.
For example, for a single row, the 'Item Name', 'Batch Number', 'Expiry Date', 'MRP', 'UOM', 'Total Invoice Cases', 'Price/Piece', and 'Taxable Value' must all belong to that same row. Do NOT align the 'Batch Number' of one row with the 'Item Name' of another row.

Please extract each item with:
1. "name": The exact name of the item (from 'Item Name' column).
2. "mrp": The Maximum Retail Price per piece/packet (numeric float, from the 'MRP' column).
3. "batch_number": Read from the 'Batch Number' column.
   - WARNING: Do NOT confuse the numeric 'Hsn Code' (e.g. '21069099') with the alphanumeric 'Batch Number' (e.g. 'MAFE30', 'MAFE24').
4. "expiry_date": The expiry date from the 'Expiry Date' column. Parse it and return strictly in the format "YYYY-MM-DD" (if it is DD/MM/YY on the invoice, convert it, e.g., 27/09/26 -> 2026-09-27).
5. "invoice_cases": The quantity of cases purchased from the 'Total Invoice Cases' column.
6. "packs_per_case": The packing quantity per case from the 'UOM' column (integer, e.g., 100, 516, 312).
7. "buy_price_per_piece": The buy price per piece without tax. Read this strictly from the 'Price/Piece' column (numeric float).
8. "net_amount": The gross amount BEFORE any discount. Read from the 'Net Amt.' column (numeric float).
   This equals: invoice_cases x packs_per_case x buy_price_per_piece.
9. "cst_discount": The total discount amount applied to this row (sum of 'GST Discount', 'CST Discount', 'Total Scheme Dis.', and any other scheme/trade discounts applied to this row). (numeric float)
   If no discount exists, use 0.0.
10. "taxable_value": The net taxable amount AFTER all discounts, BEFORE GST. Read STRICTLY from the 'Taxable Value' column.
    CRITICAL: taxable_value = net_amount - cst_discount. It is ALWAYS less than or equal to net_amount.
    If 'Taxable Value' column is missing or empty, compute as: net_amount - cst_discount.
11. "gst_percent": The GST tax percentage applied to this item from the 'GST (%)' column (numeric float, e.g., 5.0, 12.0, 18.0).
12. "offer_secondary_received": Look for any FREE or OFFER rows in the invoice (rows where Price/Piece = 0 or marked as 'Free'/'Scheme'/'Offer'/'0.00' price).
    If a free row matches an item above (same product name), set this field to the quantity of free units.
    Otherwise use 0.

CRITICAL MATHEMATICAL CHECK FOR ACCURACY:
Before outputting, you MUST mathematically verify each row.
For every row, ensure that:
`net_amount` is approximately equal to `invoice_cases * packs_per_case * buy_price_per_piece`.
`taxable_value` is approximately equal to `net_amount - cst_discount`.
If taxable_value > net_amount, you have read the wrong column. Recheck and fix.
For example:
- S No 1 (All In One): cases = 2, packs_per_case = 100, buy_price = 14.5407. Verification: 2 * 100 * 14.5407 = 2908.14. This matches the row's Taxable Value of 2,908.14. So cases MUST be 2 (NOT 3).
- S No 2 (Aloo Bhujia): cases = 3, packs_per_case = 100, buy_price = 14.5407. Verification: 3 * 100 * 14.5407 = 4362.21. This matches the row's Taxable Value of 4,362.21. So cases MUST be 3 (NOT 2).
If you find a mathematical mismatch (e.g. if you wrote cases = 3 for S No 1, but its Taxable Value is 2,908.14), you have mixed up adjacent row values. Re-scan the image, align the columns correctly for that row, and fix it.

Return a JSON object with the keys "invoice_number" and "rawItems" containing the parsed results in a code block. Do not write any markdown descriptions or introductory text.
"""




def compress_image(image_bytes: bytes) -> bytes:
    """Resize and compress image to speed up upload and API response times."""
    # If image is already very small (< 150KB), don't compress it
    if len(image_bytes) < 150 * 1024:
        logger.info("Image size is already under 150KB. Skipping compression.")
        return image_bytes
        
    try:
        img = Image.open(io.BytesIO(image_bytes))
        
        # Auto-rotate image if it has orientation metadata
        try:
            from PIL import ImageOps
            img = ImageOps.exif_transpose(img)
            logger.info("Automatically transposed/rotated image based on EXIF orientation metadata.")
        except Exception as rotation_err:
            logger.warning(f"Failed to auto-rotate image using EXIF: {rotation_err}")
        
        # Convert RGBA to RGB if necessary
        if img.mode in ('RGBA', 'LA') or (img.mode == 'P' and 'transparency' in img.info):
            img = img.convert('RGB')
            
        # Max dimensions for fast OCR processing while preserving text clarity
        # Reduced from 1800 to 1400 — smaller upload = faster Gemini response
        max_size = (1400, 1400)
        img.thumbnail(max_size, Image.Resampling.LANCZOS)
        
        out_io = io.BytesIO()
        img.save(out_io, format="JPEG", quality=82)
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
        
        # Determine MIME type based on extension or content-type
        content_type = file.content_type
        if not content_type:
            ext = os.path.splitext(file.filename)[1].lower()
            if ext == '.pdf':
                content_type = 'application/pdf'
            elif ext in ['.jpg', '.jpeg']:
                content_type = 'image/jpeg'
            elif ext == '.png':
                content_type = 'image/png'
            else:
                content_type = 'image/jpeg'

        if content_type == 'application/pdf':
            mime_type = 'application/pdf'
            base64_data = base64.b64encode(contents).decode('utf-8')
            logger.info("PDF file detected. Sending original PDF bytes directly to Gemini.")
        else:
            mime_type = 'image/jpeg'
            # Compress image to speed up upload
            processed_image = compress_image(contents)
            base64_data = base64.b64encode(processed_image).decode('utf-8')
            logger.info(f"Image file detected. Compressed size: {len(processed_image)} bytes.")
        
        # List of models to try in sequence to bypass 20 requests/day/model free tier limit or deprecated models
        models_to_try = [
            "gemini-3.1-flash-lite",
            "gemini-3-flash-preview",
            "gemini-2.5-flash",
            "gemini-3.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash"
        ]
        
        response = None
        last_error = None
        headers = {"Content-Type": "application/json"}
        
        for model_name in models_to_try:
            url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent?key={api_key}"
            
            # Formulate generation config. Only gemini-2.5 supports thinkingConfig parameter
            generation_config = {
                "responseMimeType": "application/json"
            }
            if model_name.startswith("gemini-2.5"):
                generation_config["thinkingConfig"] = {
                    "thinkingBudget": 0
                }
                
            payload = {
                "contents": [{
                    "parts": [
                        {"text": PROMPT},
                        {
                            "inlineData": {
                                "mime_type": mime_type,
                                "data": base64_data
                            }
                        }
                    ]
                }],
                "generationConfig": generation_config
            }
            
            logger.info(f"Calling Gemini REST API with model: {model_name}...")
            try:
                res = await asyncio.to_thread(requests.post, url, json=payload, headers=headers)
                if res.status_code == 200:
                    response = res
                    logger.info(f"Successfully received response from model: {model_name}")
                    break
                else:
                    logger.warning(f"Model {model_name} returned status code {res.status_code}: {res.text}")
                    last_error = f"{res.status_code}: {res.text}"
            except Exception as e:
                logger.error(f"Error calling model {model_name}: {e}")
                last_error = str(e)
                
        if not response:
            logger.error(f"All Gemini models failed. Last error: {last_error}")
            raise HTTPException(
                status_code=500,
                detail=f"All Gemini models exhausted or failed. Last error details: {last_error}"
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
        
        # Server-side safety: fix taxable_value if AI read Net Amt instead of Taxable Value
        def fix_taxable_value(items_list):
            for item in items_list:
                if isinstance(item, dict):
                    net_amt = item.get("net_amount", 0) or 0
                    cst_disc = item.get("cst_discount", 0) or 0
                    taxable = item.get("taxable_value", 0) or 0
                    # If taxable > net_amount, AI read wrong column — auto-correct
                    if net_amt > 0 and taxable > net_amt:
                        corrected = round(net_amt - cst_disc, 2)
                        logger.warning(f"Auto-correcting taxable_value for '{item.get('name')}': {taxable} -> {corrected} (net={net_amt}, disc={cst_disc})")
                        item["taxable_value"] = corrected
            return items_list

        # Ensure it matches the ScanResponse model structure {"rawItems": [...]}
        if isinstance(parsed_json, list):
            return {"rawItems": fix_taxable_value(parsed_json)}
        elif isinstance(parsed_json, dict):
            if "rawItems" in parsed_json:
                parsed_json["rawItems"] = fix_taxable_value(parsed_json.get("rawItems", []))
                return parsed_json
            # Find any list value inside the dict and use it
            for key, val in parsed_json.items():
                if isinstance(val, list):
                    parsed_json[key] = fix_taxable_value(val)
                    return {"rawItems": parsed_json[key]}
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

async def call_gemini_with_fallback(prompt: str, system_instruction: Optional[str] = None, json_mode: bool = False) -> str:
    models_to_try = [
        "gemini-3.1-flash-lite",
        "gemini-3-flash-preview",
        "gemini-2.5-flash",
        "gemini-3.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-1.5-flash"
    ]
    
    last_error = None
    headers = {"Content-Type": "application/json"}
    
    for model_name in models_to_try:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent?key={api_key}"
        
        generation_config = {}
        if json_mode:
            generation_config["responseMimeType"] = "application/json"
            
        if model_name.startswith("gemini-2.5"):
            generation_config["thinkingConfig"] = {
                "thinkingBudget": 0
            }
            
        payload = {
            "contents": [{
                "parts": [{"text": prompt}]
            }],
            "generationConfig": generation_config
        }
        
        if system_instruction:
            payload["systemInstruction"] = {
                "parts": [{"text": system_instruction}]
            }
            
        logger.info(f"Calling Gemini with model: {model_name}...")
        try:
            res = await asyncio.to_thread(requests.post, url, json=payload, headers=headers)
            if res.status_code == 200:
                response_json = res.json()
                text = response_json['candidates'][0]['content']['parts'][0]['text'].strip()
                logger.info(f"Successfully received response from model: {model_name}")
                return text
            else:
                logger.warning(f"Model {model_name} returned status code {res.status_code}: {res.text}")
                last_error = f"{res.status_code}: {res.text}"
        except Exception as e:
            logger.error(f"Error calling model {model_name}: {e}")
            last_error = str(e)
            
    raise HTTPException(
        status_code=500,
        detail=f"All Gemini models exhausted or failed. Last error: {last_error}"
    )

class TextGenerationRequest(BaseModel):
    prompt: str

class StructuredRequest(BaseModel):
    prompt: str
    systemInstruction: Optional[str] = None

@app.post("/ocr/generate-text")
async def generate_text(req: TextGenerationRequest):
    if not api_key:
        raise HTTPException(status_code=500, detail="GEMINI_API_KEY is not configured.")
    text = await call_gemini_with_fallback(req.prompt, json_mode=False)
    return {"text": text}

@app.post("/ocr/parse-structured")
async def parse_structured(req: StructuredRequest):
    if not api_key:
        raise HTTPException(status_code=500, detail="GEMINI_API_KEY is not configured.")
    response_text = await call_gemini_with_fallback(req.prompt, req.systemInstruction, json_mode=True)
    
    # Clean JSON fences if present
    if "```json" in response_text:
        response_text = response_text.split("```json")[1].split("```")[0].strip()
    elif "```" in response_text:
        response_text = response_text.split("```")[1].split("```")[0].strip()
        
    try:
        parsed_json = json.loads(response_text)
        return parsed_json
    except json.JSONDecodeError:
        return {"rawText": response_text}

if __name__ == "__main__":
    import uvicorn
    # Start server on port 8087
    uvicorn.run(app, host="127.0.0.1", port=8087)
