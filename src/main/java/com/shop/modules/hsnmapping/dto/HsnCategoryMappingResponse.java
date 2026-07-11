package com.shop.modules.hsnmapping.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class HsnCategoryMappingResponse {
    private UUID id;
    private String categoryKey;
    private String hsnCode;
    private UUID updatedBy;
    private String updatedByName;
    private LocalDateTime updatedAt;
}
