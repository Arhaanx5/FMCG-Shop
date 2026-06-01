package com.shop.modules.damage.dto;

import com.shop.modules.damage.DamageReason;
import com.shop.modules.product.UnitType;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DamageResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private String brand;
    private String batchNumber;
    private UnitType unitType;
    private Integer quantity;
    private DamageReason reason;
    private BigDecimal valueLoss;
    private com.shop.modules.damage.UnitLevel unitLevel;
    private com.shop.modules.damage.ClaimStatus claimStatus;
    private String notes;
    private String loggedBy;
    private LocalDateTime loggedAt;
}