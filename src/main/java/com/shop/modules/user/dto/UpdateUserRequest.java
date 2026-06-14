package com.shop.modules.user.dto;

import com.shop.modules.user.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for updating an existing user.
 * Password is optional — if null or blank, the existing password is retained.
 * If provided, it must meet the strength requirements.
 */
@Data
public class UpdateUserRequest {

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

    /**
     * Password is optional on update.
     * If provided (non-null, non-blank) it MUST satisfy the strength policy.
     * @Size and @Pattern only activate when the value is non-null (Jakarta Validation
     * skips null values for @Size and @Pattern by default).
     */
    @Size(min = 8, max = 72,
            message = "Password must be between 8 and 72 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,72}$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character (@$!%*?&)"
    )
    private String password;

    private java.math.BigDecimal monthlySalary;
}
