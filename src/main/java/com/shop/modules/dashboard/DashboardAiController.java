package com.shop.modules.dashboard;

import com.shop.common.ApiResponse;
import com.shop.modules.dashboard.dto.DashboardHealthReportResponse;
import com.shop.modules.dashboard.dto.TrendSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard/ai")
@RequiredArgsConstructor
public class DashboardAiController {

    private final DashboardAiService dashboardAiService;

    @GetMapping("/insights")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAiInsights(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        String insights = dashboardAiService.generateInsights(year, month, force);
        Map<String, String> result = new HashMap<>();
        result.put("insights", insights);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, String>>> chatWithDashboard(
            @RequestParam int year,
            @RequestParam int month,
            @RequestBody Map<String, String> requestBody) {
        String message = requestBody.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Message cannot be empty"));
        }
        String reply = dashboardAiService.chatWithDashboard(message, year, month);
        Map<String, String> result = new HashMap<>();
        result.put("reply", reply);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/health-report")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<DashboardHealthReportResponse>> getHealthReport(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        DashboardHealthReportResponse report = dashboardAiService.generateHealthReport(year, month, force);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/health-report/trend")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<TrendSummaryResponse>> getHealthReportTrend(
            @RequestParam(required = false, defaultValue = "12") int months) {
        TrendSummaryResponse trend = dashboardAiService.getTrendData(months);
        return ResponseEntity.ok(ApiResponse.success(trend));
    }
}
