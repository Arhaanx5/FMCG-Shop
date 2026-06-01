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
    private BigDecimal totalPending;
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
}