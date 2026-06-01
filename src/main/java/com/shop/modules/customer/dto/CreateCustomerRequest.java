package com.shop.modules.customer.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateCustomerRequest {

    @NotBlank(message = "Customer name cannot be blank")
    @Size(min = 2, max = 100,
            message = "Name must be between 2 and 100 characters")
    private String name;

    @Size(max = 150,
            message = "Shop name cannot exceed 150 characters")
    private String shopName;

    @NotBlank(message = "Phone cannot be blank")
    @Pattern(regexp = "^[0-9]{10}$",
            message = "Phone must be exactly 10 digits")
    private String phone;

    private UUID areaId;

    private Double latitude;
    private Double longitude;
    private String locationMethod;

    @PositiveOrZero(message = "Opening balance cannot be negative")
    private BigDecimal openingBalance = BigDecimal.ZERO;

    private Boolean isNpa = false;

    @PositiveOrZero(message = "Credit limit cannot be negative")
    private BigDecimal creditLimit;
}