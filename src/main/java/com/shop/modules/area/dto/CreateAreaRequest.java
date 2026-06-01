package com.shop.modules.area.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAreaRequest {

    @NotBlank(message = "Area name cannot be blank")
    @Size(min = 2, max = 100,
            message = "Name must be between 2 and 100 characters")
    private String name;

    @Size(max = 255,
            message = "Description cannot exceed 255 characters")
    private String description;

    private java.util.UUID salesmanId;
}