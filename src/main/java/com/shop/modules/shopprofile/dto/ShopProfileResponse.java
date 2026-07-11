package com.shop.modules.shopprofile.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ShopProfileResponse {
    private UUID id;
    private String companyName;
    private String gstin;
    private String fssai;
    private String phone;
    private String address;
    private String stateCode;
    private String stateName;
    private UUID updatedBy;
    private String updatedByName;
    private LocalDateTime updatedAt;
}
