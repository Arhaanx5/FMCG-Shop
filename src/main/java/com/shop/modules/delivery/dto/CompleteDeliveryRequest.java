package com.shop.modules.delivery.dto;

import com.shop.modules.delivery.DeliveryStatus;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CompleteDeliveryRequest {
    private DeliveryStatus status; // DELIVERED, FAILED, PARTIAL, etc.
    private String paymentMode; // CASH, UPI, UDHAR
    private BigDecimal amountCollected;
    private String notes;
    private String otpCode;
}
