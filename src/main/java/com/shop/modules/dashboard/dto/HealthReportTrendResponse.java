package com.shop.modules.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthReportTrendResponse {
    private int year;
    private int month;
    private String monthName; // e.g. "Jan", "Feb"
    private Integer overallScore; // Nullable for NO_DATA
    private Integer profitabilityScore;
    private Integer cashFlowScore;
    private Integer inventoryScore;
    private Integer customerScore;
    private Integer receivablesScore;
    private Integer suppliersScore;
    private Integer operationalScore;
    private BigDecimal revenue;
    private BigDecimal netProfit;
    private BigDecimal totalExpenses;
    private String status; // HEALTHY, STABLE, DECLINING, AT_RISK, NO_DATA
}
