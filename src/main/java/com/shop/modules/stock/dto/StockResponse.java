package com.shop.modules.stock.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class StockResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private String brand;
    private String category;
    private String primaryUnit;
    private String secondaryUnit;
    private Integer secondaryPerPrimary;
    private Integer totalPrimaryUnits;
    private Integer totalSecondaryUnits;
    private Boolean hasOpenPrimary;
    private Integer openPrimaryRemaining;
    private Boolean isLowStock;
    private Integer lowStockAlert;
    private String lowStockUnit;
    private LocalDateTime lastUpdated;
}