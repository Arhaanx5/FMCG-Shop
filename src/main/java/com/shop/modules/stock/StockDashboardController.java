package com.shop.modules.stock;

import com.shop.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/stock/dashboard")
@RequiredArgsConstructor
public class StockDashboardController {

    private final StockDashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<StockDashboardService.DashboardSummary>> getSummary() {
        StockDashboardService.DashboardSummary summary = dashboardService.getDashboardSummary();
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/monthly-flow")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockDashboardService.MonthlyFlowDTO>>> getMonthlyFlow(
            @RequestParam(value = "months", defaultValue = "6") int months) {
        List<StockDashboardService.MonthlyFlowDTO> flow = dashboardService.getMonthlyInventoryFlow(months);
        return ResponseEntity.ok(ApiResponse.success(flow));
    }
}
