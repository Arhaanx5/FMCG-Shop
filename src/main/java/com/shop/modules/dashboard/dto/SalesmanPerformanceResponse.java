package com.shop.modules.dashboard.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesmanPerformanceResponse {
    private UUID salesmanId;
    private String salesmanName;
    private String salesmanPhone;
    private List<String> assignedAreas;
    private BigDecimal totalRevenueGenerated;
    private BigDecimal totalCollectionsMade;
    private BigDecimal activeRouteCredit;
    private long activeCustomersCount;
}
