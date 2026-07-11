package com.shop.modules.stock.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BatchHistoryResponse {
    
    private StockBatchResponse batchDetails;
    private StockSummary stockSummary;
    private List<MovementHistoryItem> history;

    @Data
    @Builder
    public static class StockSummary {
        private Integer secondaryRemaining;
        private Integer offerSecondaryRemaining;
        private Integer totalSecondaryRemaining;
        private Integer boxesRemaining;
        private Integer looseUnitsRemaining;
    }

    @Data
    @Builder
    public static class MovementHistoryItem {
        private UUID id;
        private LocalDateTime timestamp;
        private String movementType;
        private Integer quantity; // Quantity changed (e.g. +630 or -1)
        private Integer quantityBefore;
        private Integer quantityAfter;
        private BigDecimal unitPrice;
        private BigDecimal totalValue;
        private String referenceNumber;
        private String remarks;
        private String username;
    }
}
