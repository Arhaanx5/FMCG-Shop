package com.shop.modules.stock;

import com.shop.common.ApiResponse;
import com.shop.modules.product.Product;
import com.shop.modules.stock.dto.ReceiveStockRequest;
import com.shop.modules.stock.dto.StockBatchResponse;
import com.shop.modules.stock.dto.StockResponse;
import com.shop.modules.stock.dto.BatchHistoryResponse;
import com.shop.modules.billing.BillRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final StockReportService reportService;
    private final BillRepository billRepository;
    private final StockBatchRepository batchRepository;
    private final StockMovementRepository movementRepository;
    private final jakarta.validation.Validator validator;


    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockResponse>>> getAllStock() {
        List<StockResponse> stockList = stockService.getAllStock()
                .stream()
                .map(reportService::toStockResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(stockList));
    }

    @GetMapping("/paged")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Page<StockResponse>>> getAllStockPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        Page<StockResponse> result = reportService.getFilteredStockPaged(page, size, search, category, status);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public ResponseEntity<ApiResponse<StockResponse>> getByProduct(
            @PathVariable UUID productId) {
        Stock stock = stockService.getStockByProduct(productId);
        return ResponseEntity.ok(ApiResponse.success(reportService.toStockResponse(stock)));
    }

    @GetMapping("/batches/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public ResponseEntity<ApiResponse<List<StockBatchResponse>>> getBatches(
            @PathVariable UUID productId) {
        List<StockBatchResponse> batches = stockService
                .getBatchesByProduct(productId)
                .stream()
                .map(reportService::toBatchResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @GetMapping("/expiring-soon")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockBatchResponse>>> getExpiringSoon() {
        List<StockBatchResponse> batches = stockService.getExpiringSoon()
                .stream()
                .map(reportService::toBatchResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    /**
     * Real-time batch-exists check for frontend forms.
     * Returns whether a non-exhausted batch exists for the given product + batchNumber,
     * and whether the price matches (to determine top-up eligibility).
     */
    @GetMapping("/batch-exists")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> checkBatchExists(
            @RequestParam UUID productId,
            @RequestParam String batchNumber,
            @RequestParam(required = false) java.math.BigDecimal buyPrice) {

        java.util.Map<String, Object> result = new java.util.HashMap<>();

        batchRepository.findByProductIdAndBatchNumberIgnoreCaseAndExhaustedFalse(productId, batchNumber.trim())
            .ifPresentOrElse(batch -> {
                result.put("exists", true);
                result.put("priceMatch", buyPrice == null || batch.getBuyPriceWithoutTax().compareTo(buyPrice) == 0);
                result.put("existingPrice", batch.getBuyPriceWithoutTax());
                result.put("secondaryRemaining", batch.getSecondaryRemaining());
                result.put("offerSecondaryRemaining", batch.getOfferSecondaryRemaining());
                result.put("expiryDate", batch.getExpiryDate());
                result.put("invoiceNumber", batch.getInvoiceNumber());
                result.put("batchId", batch.getId());
            }, () -> result.put("exists", false));

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/receive")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<StockBatchResponse>> receiveStock(
            @Valid @RequestBody ReceiveStockRequest req,
            java.security.Principal principal) {

        if (req.getPrimaryReceived() == 0
                && req.getExtraSecondaryReceived() == 0) {
            throw new RuntimeException("Must receive at least some stock");
        }

        StockService.ReceiveStockRequest serviceReq = new StockService.ReceiveStockRequest();
        serviceReq.setProductId(req.getProductId());
        serviceReq.setBatchNumber(req.getBatchNumber());
        serviceReq.setPrimaryReceived(req.getPrimaryReceived());
        serviceReq.setExtraSecondaryReceived(req.getExtraSecondaryReceived());
        serviceReq.setOfferSecondaryReceived(req.getOfferSecondaryReceived());
        serviceReq.setBuyPriceWithoutTax(req.getBuyPriceWithoutTax());
        serviceReq.setExpiryDate(req.getExpiryDate());
        serviceReq.setSupplierName(req.getSupplierName());
        serviceReq.setSellPricePrimary(req.getSellPricePrimary());
        serviceReq.setSellPriceSecondary(req.getSellPriceSecondary());
        serviceReq.setLogAsExpense(req.isLogAsExpense());
        serviceReq.setGstPercent(req.getGstPercent());
        
        // Redesign properties
        serviceReq.setInvoiceNumber(req.getSupplierInvoiceNumber());
        serviceReq.setSupplierInvoiceNumber(req.getSupplierInvoiceNumber());
        serviceReq.setSupplierInvoiceDate(req.getSupplierInvoiceDate());
        serviceReq.setStockReceivedDate(req.getStockReceivedDate());
        serviceReq.setManufacturingDate(req.getManufacturingDate());
        serviceReq.setRemarks(req.getRemarks());
        serviceReq.setReceiveSource(req.getReceiveSource());

        String username = principal != null ? principal.getName() : "System";
        StockBatch batch = stockService.receiveStock(serviceReq, username);

        return ResponseEntity.ok(
                ApiResponse.success("Stock received successfully", reportService.toBatchResponse(batch)));
    }

    @PostMapping("/receive-bulk")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockBatchResponse>>> receiveStockBulk(
            @RequestBody List<ReceiveStockRequest> requests,
            java.security.Principal principal) {

        String username = principal != null ? principal.getName() : "System";
        List<StockBatchResponse> responses = new ArrayList<>();

        // Check for duplicate batch numbers within the same bulk request (same productId + batchNumber)
        java.util.Set<String> seenBatchKeys = new java.util.HashSet<>();
        for (ReceiveStockRequest req : requests) {
            if (req.getBatchNumber() != null && !req.getBatchNumber().isBlank() && req.getProductId() != null) {
                String key = req.getProductId().toString() + "|" + req.getBatchNumber().trim().toUpperCase();
                if (!seenBatchKeys.add(key)) {
                    throw new RuntimeException(
                        "Duplicate batch number '" + req.getBatchNumber().trim() +
                        "' found in the same bulk request for the same product. Each product must have a unique batch number.");
                }
            }
        }

        for (ReceiveStockRequest req : requests) {
            if (req.getPrimaryReceived() == 0 && req.getExtraSecondaryReceived() == 0 && req.getOfferSecondaryReceived() == 0) {
                continue;
            }

            var violations = validator.validate(req);
            if (!violations.isEmpty()) {
                String productName = "Unknown Product";
                try {
                    productName = stockService.getStockByProduct(req.getProductId()).getProduct().getName();
                } catch (Exception ignored) {}
                
                String errorMsg = violations.stream()
                        .map(v -> v.getMessage())
                        .collect(Collectors.joining(", "));
                throw new RuntimeException("Product '" + productName + "' validation failed: " + errorMsg);
            }

            StockService.ReceiveStockRequest serviceReq = new StockService.ReceiveStockRequest();
            serviceReq.setProductId(req.getProductId());
            serviceReq.setBatchNumber(req.getBatchNumber());
            serviceReq.setPrimaryReceived(req.getPrimaryReceived());
            serviceReq.setExtraSecondaryReceived(req.getExtraSecondaryReceived());
            serviceReq.setOfferSecondaryReceived(req.getOfferSecondaryReceived());
            serviceReq.setBuyPriceWithoutTax(req.getBuyPriceWithoutTax());
            serviceReq.setExpiryDate(req.getExpiryDate());
            serviceReq.setSupplierName(req.getSupplierName());
            serviceReq.setSellPricePrimary(req.getSellPricePrimary());
            serviceReq.setSellPriceSecondary(req.getSellPriceSecondary());
            serviceReq.setLogAsExpense(req.isLogAsExpense());
            serviceReq.setGstPercent(req.getGstPercent());

            // Redesign properties
            serviceReq.setInvoiceNumber(req.getSupplierInvoiceNumber());
            serviceReq.setSupplierInvoiceNumber(req.getSupplierInvoiceNumber());
            serviceReq.setSupplierInvoiceDate(req.getSupplierInvoiceDate());
            serviceReq.setStockReceivedDate(req.getStockReceivedDate());
            serviceReq.setManufacturingDate(req.getManufacturingDate());
            serviceReq.setRemarks(req.getRemarks());
            serviceReq.setReceiveSource(req.getReceiveSource());

            StockBatch batch = stockService.receiveStock(serviceReq, username);
            responses.add(reportService.toBatchResponse(batch));
        }

        return ResponseEntity.ok(ApiResponse.success("Bulk stock received successfully", responses));
    }

    @GetMapping("/purchases")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<com.shop.modules.stock.dto.PurchaseTransactionResponse>>> getPurchases() {
        List<StockMovement> movements = movementRepository.findAllPurchaseAndOfferMovements();
        
        List<StockMovement> paidMovements = new ArrayList<>();
        Map<String, Integer> offerMap = new HashMap<>(); // key: batchId only — offer may come from different invoice (top-up)
        
        for (StockMovement m : movements) {
            String key = (m.getBatch() != null ? m.getBatch().getId().toString() : "");
            if ("OFFER_RECEIVE".equals(m.getMovementType())) {
                offerMap.put(key, offerMap.getOrDefault(key, 0) + Math.abs(m.getQuantity()));
            } else {
                paidMovements.add(m);
            }
        }

        List<com.shop.modules.stock.dto.PurchaseTransactionResponse> responses = paidMovements.stream()
                .map(m -> {
                    StockBatch b = m.getBatch();
                    Product p = m.getProduct();
                    int ratio = m.getSecondaryPerPrimary() != null ? m.getSecondaryPerPrimary()
                            : ((p != null && p.getSecondaryPerPrimary() != null && p.getSecondaryPerPrimary() > 0) ? p.getSecondaryPerPrimary() : 1);
                    
                    BigDecimal buyPriceWithoutTax = m.getBuyPriceWithoutTax() != null ? m.getBuyPriceWithoutTax()
                            : (b != null ? b.getBuyPriceWithoutTax() : BigDecimal.ZERO);
                    BigDecimal gstPercent = m.getGstPercent() != null ? m.getGstPercent()
                            : (b != null ? b.getGstPercent() : BigDecimal.ZERO);
                    BigDecimal gstRate = gstPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                    BigDecimal taxAmount = buyPriceWithoutTax.multiply(gstRate).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal buyPriceWithTax = m.getBuyPriceWithTax() != null ? m.getBuyPriceWithTax()
                            : buyPriceWithoutTax.add(taxAmount);

                    String key = (b != null ? b.getId().toString() : "");
                    int offerUnits = offerMap.getOrDefault(key, 0);

                    int secondaryRemaining = b != null ? b.getSecondaryRemaining() : 0;
                    int offerSecondaryRemaining = b != null ? (b.getOfferSecondaryRemaining() != null ? b.getOfferSecondaryRemaining() : 0) : 0;
                    String receiveSource = m.getReceiveSource() != null ? m.getReceiveSource()
                            : (b != null ? b.getReceiveSource() : "BULK_RECEIVE");

                    return com.shop.modules.stock.dto.PurchaseTransactionResponse.builder()
                            .id(m.getId())
                            .invoiceNumber(m.getReferenceNumber())
                            .supplierName(m.getSupplierName() != null ? m.getSupplierName() : (b != null ? b.getSupplierName() : "Unknown"))
                            .supplierInvoiceDate(m.getSupplierInvoiceDate() != null ? m.getSupplierInvoiceDate() : (b != null ? b.getSupplierInvoiceDate() : m.getTimestamp().toLocalDate()))
                            .productName(p != null ? p.getName() : "Unknown Product")
                            .brand(p != null ? p.getBrand() : "N/A")
                            .secondaryReceived(Math.abs(m.getQuantity()))
                            .secondaryPerPrimary(ratio)
                            .buyPriceWithoutTax(buyPriceWithoutTax)
                            .buyPriceWithTax(buyPriceWithTax)
                            .gstPercent(gstPercent)
                            .offerSecondaryReceived(offerUnits)
                            .secondaryRemaining(secondaryRemaining)
                            .offerSecondaryRemaining(offerSecondaryRemaining)
                            .receiveSource(receiveSource)
                            .batchNumber(b != null ? b.getBatchNumber() : "N/A")
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<?>> getBatches(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (date != null) {
            List<StockBatchResponse> responses = stockService.getBatchesByDate(date)
                    .stream()
                    .map(reportService::toBatchResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success(responses));
        } else {
            Page<StockBatchResponse> responses = stockService.getRecentBatchesPaged(page, size, search)
                    .map(reportService::toBatchResponse);
            return ResponseEntity.ok(ApiResponse.success(responses));
        }
    }

    @GetMapping("/batches/invoice/{invoiceNumber}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockBatchResponse>>> getBatchesByInvoice(
            @PathVariable String invoiceNumber) {
        List<StockBatchResponse> responses = stockService.getBatchesByInvoice(invoiceNumber)
                .stream()
                .map(reportService::toBatchResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(responses));
    }


    @GetMapping("/batches/{batchId}/history")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<BatchHistoryResponse>> getBatchHistory(@PathVariable UUID batchId) {
        StockBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Stock batch not found with ID: " + batchId));

        StockBatchResponse batchResponse = reportService.toBatchResponse(batch);
        
        List<StockMovement> movements = movementRepository.findByBatchIdOrderByTimestampAsc(batchId);
        
        int secondaryRemaining = batch.getSecondaryRemaining() != null ? batch.getSecondaryRemaining() : 0;
        int offerRemaining = batch.getOfferSecondaryRemaining() != null ? batch.getOfferSecondaryRemaining() : 0;
        int totalRemaining = secondaryRemaining + offerRemaining;
        int ratio = (batch.getProduct() != null && batch.getProduct().getSecondaryPerPrimary() != null) 
                ? batch.getProduct().getSecondaryPerPrimary() : 1;
        
        int boxesRemaining = totalRemaining / ratio;
        int looseRemaining = totalRemaining % ratio;

        BatchHistoryResponse.StockSummary summary = BatchHistoryResponse.StockSummary.builder()
                .secondaryRemaining(secondaryRemaining)
                .offerSecondaryRemaining(offerRemaining)
                .totalSecondaryRemaining(totalRemaining)
                .boxesRemaining(boxesRemaining)
                .looseUnitsRemaining(looseRemaining)
                .build();

        List<BatchHistoryResponse.MovementHistoryItem> historyItems = movements.stream()
                .map(m -> BatchHistoryResponse.MovementHistoryItem.builder()
                        .id(m.getId())
                        .timestamp(m.getTimestamp())
                        .movementType(m.getMovementType())
                        .quantity(m.getQuantity())
                        .quantityBefore(m.getQuantityBefore())
                        .quantityAfter(m.getQuantityAfter())
                        .unitPrice(m.getUnitPrice())
                        .totalValue(m.getTotalValue())
                        .referenceNumber(m.getReferenceNumber())
                        .remarks(m.getRemarks())
                        .username(m.getUsername())
                        .build())
                .collect(Collectors.toList());

        BatchHistoryResponse response = BatchHistoryResponse.builder()
                .batchDetails(batchResponse)
                .stockSummary(summary)
                .history(historyItems)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/batches/{batchId}/adjust")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> adjustStock(
            @PathVariable UUID batchId,
            @Valid @RequestBody StockController.AdjustStockRequest req,
            java.security.Principal principal) {
        
        String username = principal != null ? principal.getName() : "System";
        stockService.adjustStock(batchId, req.getNewSecondaryRemaining(), req.getNewOfferSecondaryRemaining(), req.getNewBuyPriceWithoutTax(), req.getReason(), username);
        
        return ResponseEntity.ok(ApiResponse.success("Stock batch adjusted successfully", null));
    }

    @PostMapping("/batches/{batchId}/deduct-offer")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deductOfferUnits(
            @PathVariable UUID batchId,
            @RequestParam int quantity) {
        stockService.deductOfferUnits(batchId, quantity);
        return ResponseEntity.ok(ApiResponse.success("Offer units deducted successfully", null));
    }

    @PostMapping("/batches/{batchId}/write-off-expiry")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> writeOffExpiry(
            @PathVariable UUID batchId,
            java.security.Principal principal) {
        
        String username = principal != null ? principal.getName() : "System";
        stockService.writeOffExpiredBatch(batchId, username);
        
        return ResponseEntity.ok(ApiResponse.success("Expired stock written off successfully", null));
    }

    @GetMapping("/adjustments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<StockAdjustmentLog>>> getAdjustmentLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        Page<StockAdjustmentLog> logs = stockService.getAdjustmentLogsPaged(page, size);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    @lombok.Data
    public static class AdjustStockRequest {
        @jakarta.validation.constraints.NotNull(message = "New remaining quantity cannot be null")
        @jakarta.validation.constraints.Min(value = 0, message = "Remaining quantity cannot be negative")
        private Integer newSecondaryRemaining;

        @jakarta.validation.constraints.Min(value = 0, message = "Offer remaining quantity cannot be negative")
        private Integer newOfferSecondaryRemaining;

        private java.math.BigDecimal newBuyPriceWithoutTax;

        @jakarta.validation.constraints.NotBlank(message = "Reason for adjustment must be specified")
        private String reason;
    }

    @PostMapping("/batches/{batchId}/mark-damage")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> markDamage(
            @PathVariable UUID batchId,
            @Valid @RequestBody MarkDamageRequest req,
            java.security.Principal principal) {
        
        String username = principal != null ? principal.getName() : "System";
        stockService.markBatchDamage(batchId, req.getQuantity(), req.getDamageType(), req.getReason(), username);
        
        return ResponseEntity.ok(ApiResponse.success("Stock damage logged successfully", null));
    }

    @lombok.Data
    public static class MarkDamageRequest {
        @jakarta.validation.constraints.NotNull(message = "Quantity cannot be null")
        @jakarta.validation.constraints.Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        @jakarta.validation.constraints.NotBlank(message = "Damage type must be specified")
        private String damageType;

        @jakarta.validation.constraints.NotBlank(message = "Reason must be specified")
        private String reason;
    }
}