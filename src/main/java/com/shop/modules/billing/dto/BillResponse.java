package com.shop.modules.billing.dto;

import com.shop.modules.billing.BillStatus;
import com.shop.modules.billing.PaymentMode;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BillResponse {

    // Bill info
    private UUID id;
    private String billNumber;
    private BillStatus status;
    private LocalDateTime createdAt;
    private String createdBy;
    private Integer version;

    // Customer info — flat, no nested object
    private UUID customerId;
    private String customerName;
    private String customerShopName;
    private String customerPhone;
    private String customerArea;

    // Amount breakdown
    private BigDecimal subtotal;
    private BigDecimal grandTotal;
    private BigDecimal discount;

    // Payment info
    private PaymentMode paymentMode;
    private BigDecimal paidAmount;
    private BigDecimal pendingAmount;
    private boolean fullyPaid;

    // GST — only show total percent and amount
    private String gstSummary;
    private BigDecimal gstTotal;
    private BigDecimal cessTotal;

    // Items — clean and minimal
    private List<BillItemResponse> items;
    private int totalItems;
    private int totalQuantity;
}