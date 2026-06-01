package com.shop.modules.khata.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class RecordPaymentRequest {

    @NotNull(message = "Customer ID is required")
    private UUID customerId;

    private UUID billId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Payment mode is required")
    private String paymentMode;

    private String notes;
}