package com.shop.modules.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionBreakdown {
    private BigDecimal totalCollected;
    private BigDecimal collectedCash;
    private BigDecimal collectedUpi;
    private BigDecimal collectedUdhar;
    private BigDecimal waivedAmount;
}
