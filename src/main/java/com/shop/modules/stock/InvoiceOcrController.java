package com.shop.modules.stock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shop.common.ApiResponse;
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

    // Python OCR service URL
    private static final String PYTHON_SERVICE_URL = "http://127.0.0.1:8087/ocr/scan-invoice";

    @Data
    public static class PythonOcrResponse {
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
        private BigDecimal gst_percent;
    }

    @PostMapping("/parse-invoice")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockMappingService.MappedStockPreview>>> parseInvoice(
            @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Uploaded file is empty"));
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
                return ResponseEntity.ok(ApiResponse.success("No items detected in invoice", new ArrayList<>()));
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
                        .gstPercent(item.getGst_percent())
                        .build();
            }).collect(Collectors.toList());

            // 4. Perform mapping logic (semantic search, unit conversions, batch duplicates)
            List<StockMappingService.MappedStockPreview> mappedPreview = mappingService.mapInvoiceItems(rawItems);

            return ResponseEntity.ok(ApiResponse.success("Invoice parsed and mapped successfully", mappedPreview));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to parse invoice: " + e.getMessage()));
        }
    }
}
