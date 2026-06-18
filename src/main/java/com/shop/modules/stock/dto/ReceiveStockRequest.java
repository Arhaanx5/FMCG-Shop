package com.shop.modules.stock.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class ReceiveStockRequest {

    @NotNull(message = "Product is required")
    private UUID productId;

    @NotBlank(message = "Batch number cannot be blank")
    @Size(min = 2, max = 100,
            message = "Batch number must be 2-100 characters")
    private String batchNumber;

    private String invoiceNumber;

    // Primary units received (BOX/CRATE/CARTON)
    @Min(value = 0,
            message = "Primary received cannot be negative")
    private int primaryReceived;

    // Extra secondary units received
    // e.g. loose ladis not in a full box
    @Min(value = 0,
            message = "Extra secondary cannot be negative")
    private int extraSecondaryReceived;

    // Free offer units (LADI/PACK) from distributor — cost = ₹0
    @Min(value = 0,
            message = "Offer units cannot be negative")
    private int offerSecondaryReceived = 0;

    // Buy price WITHOUT tax per primary unit
    @NotNull(message = "Buy price is required")
    @DecimalMin(value = "0.1",
            message = "Buy price must be greater than 0")
    private BigDecimal buyPriceWithoutTax;

    @NotNull(message = "Expiry date is required")
    @Future(message = "Expiry date must be in future")
    private LocalDate expiryDate;

    @NotBlank(message = "Supplier name cannot be blank")
    @Size(min = 2, max = 150,
            message = "Supplier name must be 2-150 characters")
    private String supplierName;

    private BigDecimal sellPricePrimary;

    private BigDecimal sellPriceSecondary;

    private BigDecimal gstPercent;

    private boolean logAsExpense = true;
}