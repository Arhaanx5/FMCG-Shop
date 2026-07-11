package com.shop.modules.damage;

import com.shop.modules.damage.dto.DamageResponse;
import org.springframework.stereotype.Component;

@Component
public class DamageMapper {

    public DamageResponse toResponse(DamageLog log) {
        return DamageResponse.builder()
                .id(log.getId())
                .productId(log.getProduct().getId())
                .productName(log.getProduct().getName())
                .brand(log.getProduct().getBrand())
                .batchNumber(log.getBatch() != null ? log.getBatch().getBatchNumber() : null)
                .unitType(log.getUnitType())
                .quantity(log.getQuantity())
                .reason(log.getReason())
                .valueLoss(log.getValueLoss())
                .unitLevel(log.getUnitLevel())
                .claimStatus(log.getClaimStatus())
                .notes(log.getNotes())
                .loggedBy(log.getLoggedBy() != null ? log.getLoggedBy().getName() : null)
                .loggedAt(log.getLoggedAt())
                .supplierName(log.getSupplierName())
                .build();
    }
}
