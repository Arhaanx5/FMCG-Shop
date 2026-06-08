package com.shop.modules.dashboard;

import com.shop.common.ApiResponse;
import com.shop.modules.dashboard.dto.DashboardResponse;
import com.shop.modules.dashboard.dto.MonthlyReportResponse;
import com.shop.modules.dashboard.dto.SalesmanPerformanceResponse;
import com.shop.modules.dashboard.dto.DashboardSummaryResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>>
    getSummary(@RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        dashboardService.getDashboardSummary(year, month)));
    }

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<DashboardResponse>>
    getToday() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        dashboardService.getTodaySummary()));
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<MonthlyReportResponse>>
    getMonthly(
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        dashboardService.getMonthlyReport(
                                year, month)));
     }

     @GetMapping("/salesmen-performance")
     @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
     public ResponseEntity<ApiResponse<List<SalesmanPerformanceResponse>>> getSalesmenPerformance() {
         return ResponseEntity.ok(
                 ApiResponse.success(
                         dashboardService.getSalesmenPerformance()));
     }

     @GetMapping("/yearly")
     @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
     public ResponseEntity<ApiResponse<MonthlyReportResponse>>
     getYearly(
             @RequestParam int year) {
         return ResponseEntity.ok(
                 ApiResponse.success(
                         dashboardService.getYearlyReport(year)));
     }
}