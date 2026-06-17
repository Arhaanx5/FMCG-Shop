package com.shop.modules.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendSummaryResponse {
    private List<HealthReportTrendResponse> trends;
    private Integer currentMonthScore;
    private Integer previousMonthScore;
    private Integer scoreDelta; // MoM change e.g. -12, +5
    private String deltaExplanation; // Hinglish text description
}
