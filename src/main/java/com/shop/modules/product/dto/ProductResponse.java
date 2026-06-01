package com.shop.modules.product.dto;

import com.shop.modules.product.Category;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ProductResponse {

    private UUID id;
    private String name;
    private String productCode;
    private String brand;
    private Category category;
    private String otherCategoryDetail;

    // GST
    private BigDecimal gstPercent;
    private BigDecimal cessPercent;

    // Unit info
    private String primaryUnit;
    private String secondaryUnit;
    private Integer secondaryPerPrimary;
    private Boolean canSellPrimary;
    private Boolean canSellSecondary;

    // Pricing
    private BigDecimal buyPriceWithoutTax;
    private BigDecimal buyPriceWithTax;
    private BigDecimal sellPricePrimary;
    private BigDecimal sellPriceSecondary;

    // Stock levels
    private Integer totalPrimaryUnits;
    private Integer totalSecondaryUnits;
    private Integer openPrimaryRemaining;
    private Boolean hasOpenPrimary;

    // Low stock
    private Integer lowStockAlert;
    private String lowStockUnit;
    private Boolean isLowStock;

    private Boolean active;
    private LocalDateTime createdAt;
}