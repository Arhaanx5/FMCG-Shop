package com.shop.modules.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchDeductionRecord {
    private UUID batchId;
    private int quantityDeducted;
}
