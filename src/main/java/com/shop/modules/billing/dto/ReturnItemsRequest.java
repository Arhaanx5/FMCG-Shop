package com.shop.modules.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class ReturnItemsRequest {

    @NotEmpty(message = "Return items list cannot be empty")
    @Valid
    private List<ReturnedItemRequest> returnedItems;

    @Data
    public static class ReturnedItemRequest {

        @NotNull(message = "Bill item ID is required")
        private UUID billItemId;

        @Min(value = 1, message = "Return quantity must be at least 1")
        private int quantityToReturn;
    }
}
