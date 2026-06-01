package com.shop.modules.user.dto;

import com.shop.modules.user.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Name cannot be blank")
    @Size(min = 2, max = 100,
            message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Phone cannot be blank")
    @Pattern(regexp = "^[0-9]{10}$",
            message = "Phone must be exactly 10 digits")
    private String phone;

    @NotNull(message = "Role is required")
    private UserRole role;

    @NotBlank(message = "Password cannot be blank")
    @Size(min = 6,
            message = "Password must be at least 6 characters")
    private String password;
}