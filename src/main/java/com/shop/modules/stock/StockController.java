package com.shop.modules.stock;

import com.shop.common.ApiResponse;
import com.shop.modules.product.Product;
import com.shop.modules.stock.dto.ReceiveStockRequest;
import com.shop.modules.stock.dto.StockBatchResponse;
import com.shop.modules.stock.dto.StockResponse;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final StockReportService reportService;
    private final BillRepository billRepository;
    private final StockBatchRepository batchRepository;
    private final jakarta.validation.Validator validator;

    public StockResponse toStockResponse(Stock stock) {
        return reportService.toStockResponse(stock);
    }

    public StockBatchResponse toBatchResponse(StockBatch batch) {
        if (batch == null) return null;
        Product product = batch.getProduct();
        boolean expiringSoon = batch.getExpiryDate() != null
                && batch.getExpiryDate().isBefore(LocalDate.now().plusDays(7));

        int ratio = product != null && product.getSecondaryPerPrimary() != null ? product.getSecondaryPerPrimary() : 1;
        BigDecimal cost = batch.getBuyPricePerSecondary(ratio);
        BigDecimal value = BigDecimal.valueOf(batch.getSecondaryRemaining()).multiply(cost);

        LocalDate recDate = batch.getStockReceivedDate() != null ? batch.getStockReceivedDate() : batch.getReceivedAt().toLocalDate();
        long age = ChronoUnit.DAYS.between(recDate, LocalDate.now());
        int sold = Math.max(0, batch.getSecondaryReceived() - batch.getSecondaryRemaining());

        return StockBatchResponse.builder()
                .id(batch.getId())
                .productId(product != null ? product.getId() : null)
                .productName(product != null ? product.getName() : null)
                .brand(product != null ? product.getBrand() : null)
                .batchNumber(batch.getBatchNumber())
                .primaryUnit(product != null ? product.getPrimaryUnit() : null)
                .secondaryUnit(product != null ? product.getSecondaryUnit() : null)
                .secondaryPerPrimary(ratio)
                .primaryReceived(batch.getPrimaryReceived())
                .secondaryReceived(batch.getSecondaryReceived())
                .secondaryRemaining(batch.getSecondaryRemaining())
                .offerSecondaryReceived(batch.getOfferSecondaryReceived() != null ? batch.getOfferSecondaryReceived() : 0)
                .offerSecondaryRemaining(batch.getOfferSecondaryRemaining() != null ? batch.getOfferSecondaryRemaining() : 0)
                .buyPriceWithoutTax(batch.getBuyPriceWithoutTax())
                .buyPriceWithTax(batch.getBuyPriceWithTax())
                .gstPercent(batch.getGstPercent())
                .expiryDate(batch.getExpiryDate())
                .supplierName(batch.getSupplierName())
                .invoiceNumber(batch.getInvoiceNumber())
                .exhausted(batch.getExhausted() != null && batch.getExhausted())
                .expiringSoon(expiringSoon)
                .receivedAt(batch.getReceivedAt())
                // Redesign properties
                .supplierInvoiceDate(batch.getSupplierInvoiceDate())
                .stockReceivedDate(batch.getStockReceivedDate())
                .manufacturingDate(batch.getManufacturingDate())
                .remarks(batch.getRemarks())
                .batchStatus(batch.getBatchStatus() != null ? batch.getBatchStatus().name() : "ACTIVE")
                .sellingPrice(product != null && product.getSellPriceSecondary() != null ? product.getSellPriceSecondary() : BigDecimal.ZERO)
                .quantitySold(sold)
                .batchValue(value.setScale(2, RoundingMode.HALF_UP))
                .stockAgeDays(age)
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockResponse>>> getAllStock() {
        List<StockResponse> stockList = stockService.getAllStock()
                .stream()
                .map(this::toStockResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(stockList));
    }

    @GetMapping("/paged")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Page<StockResponse>>> getAllStockPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<StockResponse> result = stockService.getAllStockPaged(page, size)
                .map(this::toStockResponse);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public ResponseEntity<ApiResponse<StockResponse>> getByProduct(
            @PathVariable UUID productId) {
        Stock stock = stockService.getStockByProduct(productId);
        return ResponseEntity.ok(ApiResponse.success(toStockResponse(stock)));
    }

    @GetMapping("/batches/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public ResponseEntity<ApiResponse<List<StockBatchResponse>>> getBatches(
            @PathVariable UUID productId) {
        List<StockBatchResponse> batches = stockService
                .getBatchesByProduct(productId)
                .stream()
                .map(this::toBatchResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @GetMapping("/expiring-soon")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockBatchResponse>>> getExpiringSoon() {
        List<StockBatchResponse> batches = stockService.getExpiringSoon()
                .stream()
                .map(this::toBatchResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(batches));
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

        String username = principal != null ? principal.getName() : "System";
        StockBatch batch = stockService.receiveStock(serviceReq, username);

        return ResponseEntity.ok(
                ApiResponse.success("Stock received successfully", toBatchResponse(batch)));
    }

    @PostMapping("/receive-bulk")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockBatchResponse>>> receiveStockBulk(
            @RequestBody List<ReceiveStockRequest> requests,
            java.security.Principal principal) {

        String username = principal != null ? principal.getName() : "System";
        List<StockBatchResponse> responses = new ArrayList<>();

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

            StockBatch batch = stockService.receiveStock(serviceReq, username);
            responses.add(toBatchResponse(batch));
        }

        return ResponseEntity.ok(ApiResponse.success("Bulk stock received successfully", responses));
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<?>> getBatches(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (date != null) {
            List<StockBatchResponse> responses = stockService.getBatchesByDate(date)
                    .stream()
                    .map(this::toBatchResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success(responses));
        } else {
            Page<StockBatchResponse> responses = stockService.getRecentBatchesPaged(page, size)
                    .map(this::toBatchResponse);
            return ResponseEntity.ok(ApiResponse.success(responses));
        }
    }

    @GetMapping("/batches/invoice/{invoiceNumber}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockBatchResponse>>> getBatchesByInvoice(
            @PathVariable String invoiceNumber) {
        List<StockBatchResponse> responses = stockService.getBatchesByInvoice(invoiceNumber)
                .stream()
                .map(this::toBatchResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(responses));
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
}