package com.shop.modules.stock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shop.common.ApiResponse;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class InvoiceOcrController {

    private final StockMappingService mappingService;
    private final StockBatchRepository batchRepository;

    // Python OCR service URL
    private static final String PYTHON_SERVICE_URL = "http://127.0.0.1:8087/ocr/scan-invoice";

    @Data
    @Builder
    public static class InvoiceScanResult {
        private String invoiceNumber;
        private boolean alreadyScanned;
        private List<StockMappingService.MappedStockPreview> items;
    }

    @Data
    public static class PythonOcrResponse {
        private String invoice_number;
        private List<PythonOcrItem> rawItems;
    }

    @Data
    public static class PythonOcrItem {
        private String name;
        private BigDecimal mrp;
        private String batch_number;
        private String expiry_date;
        private int invoice_cases;
        private int packs_per_case;
        private BigDecimal buy_price_per_piece;
        private BigDecimal net_amount;         // NEW: gross before discount
        private BigDecimal cst_discount;       // NEW: CST/scheme discount amount
        private BigDecimal taxable_value;      // after discount, before GST
        private BigDecimal gst_percent;
        private int offer_secondary_received;  // NEW: free units detected from invoice
    }

    @PostMapping("/parse-invoice")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<InvoiceScanResult>> parseInvoice(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "supplierName", required = false) String supplierName) {

        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Uploaded file is empty"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !(contentType.equalsIgnoreCase("image/jpeg") 
                || contentType.equalsIgnoreCase("image/png") 
                || contentType.equalsIgnoreCase("image/webp")
                || contentType.equalsIgnoreCase("application/pdf"))) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Only JPEG, PNG, WEBP images, and PDF documents are allowed"));
        }

        try {
            // 1. Forward the image to the Python FastAPI microservice
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // Wrap file bytes in Resource
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "invoice.jpg";
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                    PYTHON_SERVICE_URL,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode() != HttpStatus.OK) {
                return ResponseEntity.status(response.getStatusCode())
                        .body(ApiResponse.error("Python OCR service failed: " + response.getBody()));
            }

            // 2. Parse the JSON response
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            PythonOcrResponse ocrResult = objectMapper.readValue(response.getBody(), PythonOcrResponse.class);

            if (ocrResult == null || ocrResult.getRawItems() == null) {
                InvoiceScanResult emptyResult = InvoiceScanResult.builder()
                        .invoiceNumber(ocrResult != null ? ocrResult.getInvoice_number() : null)
                        .alreadyScanned(false)
                        .items(new ArrayList<>())
                        .build();
                return ResponseEntity.ok(ApiResponse.success("No items detected in invoice", emptyResult));
            }

            // 3. Map to RawInvoiceItem model
            List<StockMappingService.RawInvoiceItem> rawItems = ocrResult.getRawItems().stream().map(item -> {
                LocalDate expDate;
                try {
                    expDate = LocalDate.parse(item.getExpiry_date());
                } catch (Exception e) {
                    expDate = LocalDate.now().plusMonths(6); // Safe fallback
                }

                return StockMappingService.RawInvoiceItem.builder()
                        .name(item.getName())
                        .mrp(item.getMrp())
                        .batchNumber(item.getBatch_number() != null ? item.getBatch_number() : "TEMP-" + System.currentTimeMillis())
                        .expiryDate(expDate)
                        .invoiceCases(item.getInvoice_cases())
                        .packsPerCase(item.getPacks_per_case())
                        .buyPricePerPiece(item.getBuy_price_per_piece())
                        .taxableValue(item.getTaxable_value())
                        .gstPercent(item.getGst_percent())
                        .offerUnitsDetected(item.getOffer_secondary_received())
                        .build();
            }).collect(Collectors.toList());

            // 4. Perform mapping logic (semantic search, unit conversions, batch duplicates)
            List<StockMappingService.MappedStockPreview> mappedPreview = mappingService.mapInvoiceItems(rawItems);

            boolean alreadyScanned = false;
            if (ocrResult.getInvoice_number() != null && !ocrResult.getInvoice_number().isBlank()) {
                String supplier = (supplierName != null && !supplierName.isBlank()) ? supplierName.trim() : "Saurabh Agency";
                alreadyScanned = batchRepository.existsBySupplierNameIgnoreCaseAndInvoiceNumberIgnoreCase(supplier, ocrResult.getInvoice_number().trim());
            }

            InvoiceScanResult scanResult = InvoiceScanResult.builder()
                    .invoiceNumber(ocrResult.getInvoice_number() != null ? ocrResult.getInvoice_number().trim() : null)
                    .alreadyScanned(alreadyScanned)
                    .items(mappedPreview)
                    .build();

            return ResponseEntity.ok(ApiResponse.success("Invoice parsed and mapped successfully", scanResult));


        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to parse invoice: " + e.getMessage()));
        }
    }

    @GetMapping("/check-duplicate-invoice")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Boolean>> checkDuplicateInvoice(
            @RequestParam String supplierName,
            @RequestParam String invoiceNumber) {
        boolean exists = false;
        if (invoiceNumber != null && !invoiceNumber.isBlank()) {
            exists = batchRepository.existsBySupplierNameIgnoreCaseAndInvoiceNumberIgnoreCase(
                    supplierName.trim(), invoiceNumber.trim()
            );
        }
        return ResponseEntity.ok(ApiResponse.success(exists));
    }
}
