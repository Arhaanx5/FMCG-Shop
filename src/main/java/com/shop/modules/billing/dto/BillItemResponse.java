package com.shop.modules.billing.dto;

import com.shop.modules.product.UnitType;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class BillItemResponse {

    private java.util.UUID id;
    private java.util.UUID productId;
    private String productName;
    private String brand;
    private UnitType unitType;
    private int quantity;
    private int freeQuantity;
    private BigDecimal rate;
    private BigDecimal originalRate;

    // GST — show only percent and amount
    private BigDecimal gstPercent;
    private BigDecimal gstAmount;

    // Cess — show only percent and amount
    private BigDecimal cessPercent;
    private BigDecimal cessAmount;

    private BigDecimal total;
    private boolean offer;
    private boolean returned;
}