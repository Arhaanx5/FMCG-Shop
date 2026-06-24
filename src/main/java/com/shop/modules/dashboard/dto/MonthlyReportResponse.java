package com.shop.modules.dashboard.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class MonthlyReportResponse {

    private int year;
    private int month;

    // Revenue
    private BigDecimal totalRevenue;
    private BigDecimal totalCollected;
    private BigDecimal totalCollectedCash;
    private BigDecimal totalCollectedUpi;
    private BigDecimal totalCollectedUdhar;
    private BigDecimal totalPending;
    private BigDecimal totalWaived;
    private BigDecimal totalNewUdhar;
    private Long totalBills;

    // Expenses
    private BigDecimal totalExpenses;
    private Map<String, BigDecimal> expensesByCategory;

    // Profit
    private BigDecimal netProfit;

    // Damage
    private BigDecimal totalDamageLoss;

    // Top products
    private Map<String, Integer> topProductsByQty;

    // Period comparison
    private BigDecimal lastMonthRevenue;
    private BigDecimal lastMonthCollection;
    private BigDecimal lastMonthExpenses;
    private BigDecimal lastMonthNewUdhar;
    private BigDecimal lastYearRevenue;
    private BigDecimal lastYearCollection;

    // Always current
    private BigDecimal totalInventoryValue;
    private BigDecimal codPendingAmount;
    private Long codPendingBillsCount;
    private Integer codOverdueCount;
    private Long lowStockCount;
    private Long npaCustomersCount;
    private BigDecimal npaCustomersAmount;
    private Long oldestPendingDays;
    private BigDecimal totalOutstandingUdhar;

    // Period / Health
    private BigDecimal netProfitMarginPct;
    private BigDecimal avgBillValue;
    private BigDecimal damageLossMTD;
    private Long newCustomersThisMonth;
    private BigDecimal codSuccessRate;
    private BigDecimal avgCollectionDays;
    private Long activeCustomersToday;
    private Long activeCustomersMonth;
    private Long activeCustomersYear;

    // Sparkline/trend
    private java.util.List<DailyTrendPoint> sevenDayTrend;
}