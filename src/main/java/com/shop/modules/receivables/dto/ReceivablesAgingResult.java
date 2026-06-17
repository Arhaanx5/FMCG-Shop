package com.shop.modules.receivables.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ReceivablesAgingResult {
    private BigDecimal age30;
    private BigDecimal age60;
    private BigDecimal age90;
    private BigDecimal age90Plus;
}
