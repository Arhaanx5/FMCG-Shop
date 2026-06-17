package com.shop.modules.dashboard.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardHealthReportResponse {

    private Integer overallScore;
    private String status; // HEALTHY, STABLE, DECLINING, AT_RISK
    private String healthExplanation; // Hinglish overall summary

    private CategoryDetails profitabilityDetails;
    private CategoryDetails cashFlowDetails;
    private CategoryDetails inventoryDetails;
    private CategoryDetails customerDetails;
    private CategoryDetails receivablesDetails;
    private CategoryDetails suppliersDetails;
    private CategoryDetails operationalDetails;

    private List<ActionItem> actionChecklist;

    private RawMetrics rawMetrics;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RawMetrics {
        private BigDecimal revenue;
        private BigDecimal netProfit;
        private BigDecimal totalExpenses;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryDetails {
        private Integer score; // nullable
        private String rating; // Good, Average, Critical, N/A
        private String explanation; // Hinglish diagnosis
        private Map<String, String> kpis; // key-value metrics
        private List<String> diagnoses; // bullet points
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActionItem {
        private String task;
        private String category; // INVENTORY, RECEIVABLES, CASH_FLOW, etc.
        private String urgency; // HIGH, MEDIUM, LOW
        private String instructions; // Hinglish details
    }
}
