package com.shop.modules.stock.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    // Redesign properties
    private Integer availableStock;
    private Integer reservedStock;
    private BigDecimal avgCost;
    private BigDecimal sellingPrice;
    private BigDecimal marginPercent;
    private BigDecimal avgCostPrimary;
    private BigDecimal sellingPricePrimary;
    private LocalDate lastPurchaseDate;
    private LocalDate lastSaleDate;
    private BigDecimal inventoryValue;
    private Integer reorderLevel;
    private String status;
}