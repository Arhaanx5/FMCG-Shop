package com.shop.modules.user;

import com.shop.modules.user.dto.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .phone(user.getPhone())
                .role(user.getRole())
                .active(user.getActive())
                .mustChangePassword(user.getMustChangePassword())
                .lastLatitude(user.getLastLatitude())
                .lastLongitude(user.getLastLongitude())
                .lastLocationTime(user.getLastLocationTime())
                .monthlySalary(user.getMonthlySalary())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
