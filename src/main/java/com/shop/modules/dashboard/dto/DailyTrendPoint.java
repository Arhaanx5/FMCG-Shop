package com.shop.modules.dashboard.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyTrendPoint {
    private LocalDate date;
    private String dayName;
    private BigDecimal revenue;
    private BigDecimal collection;
    private Long bills;
    private BigDecimal newUdhar;
}
