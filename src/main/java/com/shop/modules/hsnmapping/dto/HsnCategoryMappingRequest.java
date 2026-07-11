package com.shop.modules.hsnmapping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class HsnCategoryMappingRequest {

    @NotBlank(message = "Category key is required")
    private String categoryKey;

    @NotBlank(message = "HSN code is required")
    @Pattern(regexp = "^[0-9]{4,10}$", message = "HSN Code must be between 4 and 10 digits")
    private String hsnCode;
}
