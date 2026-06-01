package com.shop.modules.area.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AreaResponse {

    private UUID id;
    private String name;
    private String description;
    private UUID salesmanId;
    private String salesmanName;
    private String salesmanPhone;
    private LocalDateTime createdAt;
}