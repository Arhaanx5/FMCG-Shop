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

    @NotBlank(message = "Supplier invoice number is required")
    private String supplierInvoiceNumber;

    @NotNull(message = "Supplier invoice date is required")
    private LocalDate supplierInvoiceDate;

    @NotNull(message = "Stock received date is required")
    private LocalDate stockReceivedDate;

    private LocalDate manufacturingDate;

    private String remarks;

    private String invoiceNumber; // legacy support

    @Min(value = 0,
            message = "Primary received cannot be negative")
    private int primaryReceived;

    @Min(value = 0,
            message = "Extra secondary cannot be negative")
    private int extraSecondaryReceived;

    @Min(value = 0,
            message = "Offer units cannot be negative")
    private int offerSecondaryReceived = 0;

    @NotNull(message = "Buy price is required")
    @DecimalMin(value = "0.1",
            message = "Buy price must be greater than 0")
    private BigDecimal buyPriceWithoutTax;

    @NotNull(message = "Expiry date is required")
    @Future(message = "Expiry Date must be in the future")
    private LocalDate expiryDate;

    @NotBlank(message = "Supplier name cannot be blank")
    @Size(min = 2, max = 150,
            message = "Supplier name must be 2-150 characters")
    private String supplierName;

    private BigDecimal sellPricePrimary;

    private BigDecimal sellPriceSecondary;

    private BigDecimal gstPercent;

    private String receiveSource;

    private boolean logAsExpense = true;
}