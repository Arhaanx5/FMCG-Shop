package com.shop.modules.khata.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PaymentResponse {

    private UUID id;
    private UUID customerId;
    private String customerName;
    private String customerShopName;
    private UUID billId;
    private String billNumber;
    private BigDecimal amount;
    private String paymentMode;
    private String notes;
    private LocalDateTime paidAt;
    private String collectedBy;
    private BigDecimal customerPendingBalance;
}