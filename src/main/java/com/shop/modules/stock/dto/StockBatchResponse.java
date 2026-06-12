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
}