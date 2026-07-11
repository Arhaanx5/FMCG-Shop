package com.shop.modules.stock.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class PurchaseTransactionResponse {
    private UUID id;
    private String invoiceNumber;
    private String supplierName;
    private LocalDate supplierInvoiceDate;
    private String productName;
    private String brand;
    private Integer secondaryReceived; // Paid only
    private Integer secondaryPerPrimary;
    private BigDecimal buyPriceWithoutTax; // Per box
    private BigDecimal buyPriceWithTax; // Per box
    private BigDecimal gstPercent;
    private Integer offerSecondaryReceived;
    private Integer secondaryRemaining;
    private Integer offerSecondaryRemaining;
    private String receiveSource;
    private String batchNumber;
}
