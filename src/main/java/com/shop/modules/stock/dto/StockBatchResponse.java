package com.shop.modules.stock.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class StockBatchResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private String brand;
    private String batchNumber;
    private String invoiceNumber;

    // Unit info
    private String primaryUnit;
    private String secondaryUnit;
    private Integer secondaryPerPrimary;

    // Quantities
    private Integer primaryReceived;
    private Integer secondaryReceived;
    private Integer secondaryRemaining;

    // Offer (free) units from distributor
    private Integer offerSecondaryReceived;
    private Integer offerSecondaryRemaining;

    // Pricing
    private BigDecimal buyPriceWithoutTax;
    private BigDecimal buyPriceWithTax;
    private BigDecimal gstPercent;

    // Batch info
    private LocalDate expiryDate;
    private String supplierName;
    private Boolean exhausted;
    private Boolean expiringSoon;
    private LocalDateTime receivedAt;

    // Redesign properties
    private LocalDate supplierInvoiceDate;
    private LocalDate stockReceivedDate;
    private LocalDate manufacturingDate;
    private String remarks;
    private String batchStatus;
    private BigDecimal sellingPrice;
    private Integer quantitySold;
    private BigDecimal batchValue;
    private Long stockAgeDays;
}