package com.shop.modules.receivables.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceivablesPendingResponse {
    private UUID customerId;
    private String customerName;
    private String shopName;
    private String phoneNumber;
    private BigDecimal pendingAmount;
    private int daysOverdue;
    private LocalDateTime lastReminderSentAt;
    private UUID billId; // oldest pending bill ID
    private boolean needsFollowUp;
    private boolean isNpa;
}

