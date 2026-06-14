package com.shop.modules.billing.dto;

import com.shop.modules.billing.PaymentMode;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.product.UnitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class CreateBillRequest {

    private BillStatus status;

    @NotBlank(message = "Customer is required")
    private String customerId;

    @NotNull(message = "Payment mode is required")
    private PaymentMode paymentMode;

    @PositiveOrZero(message = "Discount cannot be negative")
    private BigDecimal discount = BigDecimal.ZERO;

    @PositiveOrZero(message = "Paid amount cannot be negative")
    private BigDecimal paidAmount = BigDecimal.ZERO;

    private String notes;

    @NotEmpty(message = "Bill must have at least one item")
    @Valid
    private List<BillItemRequest> items;

    @Data
    public static class BillItemRequest {

        @NotBlank(message = "Product is required")
        private String productId;

        private UUID batchId;

        @NotNull(message = "Unit type is required")
        private UnitType unitType;

        @Min(value = 1,
                message = "Quantity must be at least 1")
        @jakarta.validation.constraints.Max(value = 1000000,
                message = "Quantity cannot exceed 1,000,000")
        private int quantity;

        @Min(value = 0,
                message = "Free quantity cannot be negative")
        @jakarta.validation.constraints.Max(value = 1000000,
                message = "Free quantity cannot exceed 1,000,000")
        private int freeQuantity = 0;

        private java.math.BigDecimal gstPercent;
        private java.math.BigDecimal cessPercent;
    }
}