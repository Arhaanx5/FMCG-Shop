package com.shop.modules.stock;

import com.shop.common.ApiResponse;
import com.shop.modules.product.Product;
import com.shop.modules.stock.dto.ReceiveStockRequest;
import com.shop.modules.stock.dto.StockBatchResponse;
import com.shop.modules.stock.dto.StockResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
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
                .buyPriceWithoutTax(batch.getBuyPriceWithoutTax())
                .buyPriceWithTax(batch.getBuyPriceWithTax())
                .gstPercent(batch.getGstPercent())
                .expiryDate(batch.getExpiryDate())
                .supplierName(batch.getSupplierName())
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

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<StockResponse>> getByProduct(
            @PathVariable UUID productId) {
        Stock stock = stockService.getStockByProduct(productId);
        return ResponseEntity.ok(ApiResponse.success(toStockResponse(stock)));
    }

    @GetMapping("/batches/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
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
            @Valid @RequestBody ReceiveStockRequest req) {

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
        serviceReq.setBuyPriceWithoutTax(req.getBuyPriceWithoutTax());
        serviceReq.setExpiryDate(req.getExpiryDate());
        serviceReq.setSupplierName(req.getSupplierName());

        StockBatch batch = stockService.receiveStock(serviceReq);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Stock received successfully",
                        toBatchResponse(batch)));
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

    // ── View Stock Audit Logs (Strictly Admin only) ──
    @GetMapping("/adjustments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<StockAdjustmentLog>>> getAdjustmentLogs() {
        List<StockAdjustmentLog> logs = stockService.getAdjustmentLogs();
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