package com.shop.modules.damage.dto;

import com.shop.modules.damage.DamageReason;
import com.shop.modules.product.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.util.UUID;

@Data
public class LogDamageRequest {

    @NotBlank(message = "Product is required")
    private String productId;

    private UUID batchId;

    private UnitType unitType;

    @NotNull(message = "Unit level is required")
    private com.shop.modules.damage.UnitLevel unitLevel;

    @NotNull(message = "Claim status is required")
    private com.shop.modules.damage.ClaimStatus claimStatus;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @NotNull(message = "Reason is required")
    private DamageReason reason;

    private String notes;
}