package com.shop.modules.product.dto;

import com.shop.modules.product.Category;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateProductRequest {

    @NotBlank(message = "Product name cannot be blank")
    @Size(min = 2, max = 150,
            message = "Name must be between 2 and 150 characters")
    private String name;

    @Size(max = 100,
            message = "Brand cannot exceed 100 characters")
    private String brand;

    @NotNull(message = "Category is required")
    private Category category;

    private String otherCategoryDetail;

    // GST 0 to 40%
    @NotNull(message = "GST percent is required")
    @DecimalMin(value = "0.0",
            message = "GST cannot be negative")
    @DecimalMax(value = "40.0",
            message = "GST cannot exceed 40%")
    private BigDecimal gstPercent;

    @DecimalMin(value = "0.0",
            message = "Cess cannot be negative")
    @DecimalMax(value = "100.0",
            message = "Cess cannot exceed 100%")
    private BigDecimal cessPercent = BigDecimal.ZERO;

    // Primary unit — BOX / CRATE / CARTON
    @NotBlank(message = "Primary unit is required")
    private String primaryUnit;

    // Secondary unit — LADI / BOTTLE / PACK
    @NotBlank(message = "Secondary unit is required")
    private String secondaryUnit;

    // How many secondary per primary
    @NotNull(message = "Secondary per primary is required")
    @Min(value = 1,
            message = "Must have at least 1 secondary per primary")
    private Integer secondaryPerPrimary;

    // Can sell by primary unit
    private Boolean canSellPrimary = true;

    // Can sell by secondary unit
    private Boolean canSellSecondary = true;

    // Buy price WITHOUT tax per primary unit
    @NotNull(message = "Buy price is required")
    @DecimalMin(value = "0.1",
            message = "Buy price must be greater than 0")
    private BigDecimal buyPriceWithoutTax;

    // Sell price per primary unit
    @NotNull(message = "Sell price primary is required")
    @PositiveOrZero(
            message = "Sell price cannot be negative")
    private BigDecimal sellPricePrimary;

    // Sell price per secondary unit
    @NotNull(message = "Sell price secondary is required")
    @PositiveOrZero(
            message = "Sell price cannot be negative")
    private BigDecimal sellPriceSecondary;

    // Low stock alert threshold
    @Min(value = 0,
            message = "Low stock alert cannot be negative")
    private Integer lowStockAlert = 10;

    // Which unit to check for low stock
    // PRIMARY or SECONDARY
    private String lowStockUnit = "SECONDARY";
}