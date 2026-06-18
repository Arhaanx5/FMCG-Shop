package com.shop.modules.stock;

import com.shop.common.ApiResponse;
import com.shop.modules.product.Product;
import com.shop.modules.stock.dto.ReceiveStockRequest;
import com.shop.modules.stock.dto.StockBatchResponse;
import com.shop.modules.stock.dto.StockResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    private StockResponse toStockResponse(Stock stock) {
        if (stock == null) return null;
        Product product = stock.getProduct();
        if (product == null) {
            return StockResponse.builder()
                    .id(stock.getId())
                    .totalPrimaryUnits(stock.getTotalPrimaryUnits())
                    .totalSecondaryUnits(stock.getTotalSecondaryUnits())
                    .hasOpenPrimary(stock.getHasOpenPrimary())
                    .openPrimaryRemaining(stock.getOpenPrimaryRemaining())
                    .lastUpdated(stock.getLastUpdated())
                    .build();
        }
        boolean isLowStock = stock.getTotalSecondaryUnits()
                < product.getLowStockAlert();
        return StockResponse.builder()
                .id(stock.getId())
                .productId(product.getId())
                .productName(product.getName())
                .brand(product.getBrand())
                .category(product.getCategory() != null
                        ? product.getCategory().name() : null)
                .primaryUnit(product.getPrimaryUnit())
                .secondaryUnit(product.getSecondaryUnit())
                .secondaryPerPrimary(product.getSecondaryPerPrimary())
                .totalPrimaryUnits(stock.getTotalPrimaryUnits())
                .totalSecondaryUnits(stock.getTotalSecondaryUnits())
                .hasOpenPrimary(stock.getHasOpenPrimary())
                .openPrimaryRemaining(stock.getOpenPrimaryRemaining())
                .isLowStock(isLowStock)
                .lowStockAlert(product.getLowStockAlert())
                .lowStockUnit(product.getLowStockUnit())
                .lastUpdated(stock.getLastUpdated())
                .build();
    }

    private StockBatchResponse toBatchResponse(StockBatch batch) {
        if (batch == null) return null;
        Product product = batch.getProduct();
        boolean expiringSoon = batch.getExpiryDate() != null
                && batch.getExpiryDate()
                .isBefore(LocalDate.now().plusDays(7));
        return StockBatchResponse.builder()
                .id(batch.getId())
                .productId(product != null ? product.getId() : null)
                .productName(product != null ? product.getName() : null)
                .brand(product != null ? product.getBrand() : null)
                .batchNumber(batch.getBatchNumber())
                .primaryUnit(product != null ? product.getPrimaryUnit() : null)
                .secondaryUnit(product != null ? product.getSecondaryUnit() : null)
                .secondaryPerPrimary(product != null ? product.getSecondaryPerPrimary() : 1)
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
            throw new RuntimeException(
                    "Must receive at least some stock");
        }

        StockService.ReceiveStockRequest serviceReq =
                new StockService.ReceiveStockRequest();
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
        serviceReq.setInvoiceNumber(req.getInvoiceNumber());

        String username = principal != null ? principal.getName() : "System";
        StockBatch batch = stockService.receiveStock(serviceReq, username);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Stock received successfully",
                        toBatchResponse(batch)));
    }

    @PostMapping("/receive-bulk")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockBatchResponse>>> receiveStockBulk(
            @Valid @RequestBody List<ReceiveStockRequest> requests,
            java.security.Principal principal) {

        String username = principal != null ? principal.getName() : "System";
        List<StockBatchResponse> responses = new ArrayList<>();

        for (ReceiveStockRequest req : requests) {
            if (req.getPrimaryReceived() == 0 && req.getExtraSecondaryReceived() == 0) {
                continue; // Skip empty requests
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
            serviceReq.setInvoiceNumber(req.getInvoiceNumber());

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

    // ── Adjust Stock Batch Quantity (Admin & Manager only) ──
    @PutMapping("/batches/{batchId}/adjust")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> adjustStock(
            @PathVariable UUID batchId,
            @Valid @RequestBody AdjustStockRequest req,
            java.security.Principal principal) {
        
        String username = principal != null ? principal.getName() : "System";
        stockService.adjustStock(batchId, req.getNewSecondaryRemaining(), req.getNewBuyPriceWithoutTax(), req.getReason(), username);
        
        return ResponseEntity.ok(ApiResponse.success("Stock batch adjusted successfully", null));
    }

    // ── Deduct offer (free) units from a batch ──
    // Used by billing screen when user clicks "Add Offer to Bill"
    @PostMapping("/batches/{batchId}/deduct-offer")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deductOfferUnits(
            @PathVariable UUID batchId,
            @RequestParam int quantity) {
        stockService.deductOfferUnits(batchId, quantity);
        return ResponseEntity.ok(ApiResponse.success("Offer units deducted successfully", null));
    }

    // ── Write off expired stock batch to Damage Log (Admin & Manager only) ──
    @PostMapping("/batches/{batchId}/write-off-expiry")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> writeOffExpiry(
            @PathVariable UUID batchId,
            java.security.Principal principal) {
        
        String username = principal != null ? principal.getName() : "System";
        stockService.writeOffExpiredBatch(batchId, username);
        
        return ResponseEntity.ok(ApiResponse.success("Expired stock written off successfully", null));
    }

    // ── View Stock Audit Logs (Strictly Admin only) ──
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

        private java.math.BigDecimal newBuyPriceWithoutTax;

        @jakarta.validation.constraints.NotBlank(message = "Reason for adjustment must be specified")
        private String reason;
    }
}