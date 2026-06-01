package com.shop.modules.user.dto;

import com.shop.modules.user.UserRole;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserResponse {

    private UUID id;
    private String name;
    private String phone;
    private UserRole role;
    private Boolean active;
    private Boolean mustChangePassword;
    private Double lastLatitude;
    private Double lastLongitude;
    private LocalDateTime lastLocationTime;
    private LocalDateTime createdAt;
}