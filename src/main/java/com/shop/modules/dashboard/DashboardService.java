package com.shop.modules.dashboard;

import com.shop.modules.dashboard.dto.DailyTrendPoint;
import com.shop.modules.dashboard.dto.DashboardResponse;
import com.shop.modules.dashboard.dto.DashboardSummaryResponse;
import com.shop.modules.dashboard.dto.MonthlyReportResponse;
import com.shop.modules.dashboard.dto.SalesmanPerformanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service orchestrator for Dashboard aggregation.
 * Delegates all calculation and reporting queries to specialized services.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardSummaryService dashboardSummaryService;
    private final SalesReportService salesReportService;
    private final SalesmenPerformanceService salesmenPerformanceService;

    public DashboardResponse getTodaySummary() {
        return dashboardSummaryService.getTodaySummary();
    }

    public List<DailyTrendPoint> getTrendData(int days) {
        return salesReportService.getTrendData(days);
    }

    public MonthlyReportResponse getMonthlyReport(int year, int month) {
        return salesReportService.getMonthlyReport(year, month);
    }

    public MonthlyReportResponse getYearlyReport(int year) {
        return salesReportService.getYearlyReport(year);
    }

    public List<SalesmanPerformanceResponse> getSalesmenPerformance() {
        return salesmenPerformanceService.getSalesmenPerformance();
    }

    public DashboardSummaryResponse getDashboardSummary(int year, int month) {
        return getDashboardSummary(year, month, 5);
    }

    public DashboardSummaryResponse getDashboardSummary(int year, int month, int limit) {
        return dashboardSummaryService.getDashboardSummary(year, month, limit);
    }

    public Map<String, Object> getBusinessHealth() {
        DashboardResponse today = getTodaySummary();
        Map<String, Object> health = new HashMap<>();
        health.put("netProfitMarginPct", today.getNetProfitMarginPct());
        health.put("activeCustomersToday", today.getActiveCustomersToday());
        health.put("avgBillValue", today.getAvgBillValue());
        health.put("damageLossMTD", today.getDamageLossMTD());
        health.put("npaCount", today.getNpaCustomersCount());
        health.put("npaAmount", today.getNpaCustomersAmount());
        health.put("oldestPendingDays", today.getOldestPendingDays());
        health.put("codOverdueCount", today.getCodOverdueCount());
        health.put("codSuccessRate", today.getCodSuccessRate());
        health.put("avgCollectionDays", today.getAvgCollectionDays());
        health.put("totalWaivedMTD", today.getTotalWaived());
        return health;
    }
}