package com.shop.modules.stock;

import com.shop.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/stock/reports")
@RequiredArgsConstructor
public class StockReportController {

    private final StockReportService reportService;

    @GetMapping("/valuation")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Page<StockReportService.InventoryReportRow>>> getValuationReport(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<StockReportService.InventoryReportRow> data = reportService.getInventoryValuationReport(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/expiry")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Page<StockReportService.ExpiryReportRow>>> getExpiryReport(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<StockReportService.ExpiryReportRow> data = reportService.getExpiryReport(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/aging")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Page<StockReportService.AgingReportRow>>> getAgingReport(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<StockReportService.AgingReportRow> data = reportService.getStockAgingReport(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/profitability/category")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockReportService.CategoryProfitabilityRow>>> getCategoryProfitabilityReport() {
        List<StockReportService.CategoryProfitabilityRow> data = reportService.getCategoryProfitabilityReport();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/valuation/export")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockReportService.InventoryReportRow>>> exportValuationReport() {
        List<StockReportService.InventoryReportRow> data = reportService.getInventoryValuationReportAll();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/expiry/export")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockReportService.ExpiryReportRow>>> exportExpiryReport() {
        List<StockReportService.ExpiryReportRow> data = reportService.getExpiryReportAll();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/aging/export")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockReportService.AgingReportRow>>> exportAgingReport() {
        List<StockReportService.AgingReportRow> data = reportService.getStockAgingReportAll();
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
