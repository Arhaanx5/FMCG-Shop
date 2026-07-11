package com.shop.modules.shopprofile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateShopProfileRequest {

    @NotBlank(message = "Company name is required")
    @Size(min = 2, max = 150, message = "Company name must be between 2 and 150 characters")
    private String companyName;

    @NotBlank(message = "GSTIN is required")
    @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$", 
            message = "Invalid GSTIN format")
    private String gstin;

    @Pattern(regexp = "^$|^[0-9]{14}$", message = "FSSAI must be 14 digits or empty")
    private String fssai;

    private String phone;

    private String address;

    @NotBlank(message = "State code is required")
    @Pattern(regexp = "^(0[1-9]|[1-2][0-9]|3[0-8])$", message = "Invalid GST State Code (01-38)")
    private String stateCode;

    @NotBlank(message = "State name is required")
    private String stateName;
}
