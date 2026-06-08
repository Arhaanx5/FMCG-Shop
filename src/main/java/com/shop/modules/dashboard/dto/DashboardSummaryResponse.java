package com.shop.modules.dashboard.dto;

import com.shop.modules.billing.dto.BillResponse;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class DashboardSummaryResponse {
    private DashboardResponse today;
    private MonthlyReportResponse monthly;
    private MonthlyReportResponse yearly;
    private List<BillResponse> recentBills;
}
