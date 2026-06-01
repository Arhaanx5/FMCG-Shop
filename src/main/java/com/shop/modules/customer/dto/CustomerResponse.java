package com.shop.modules.customer.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CustomerResponse {

    private UUID id;
    private String name;
    private String shopName;
    private String phone;

    // Area info
    private UUID areaId;
    private String areaName;

    // Location
    private Double latitude;
    private Double longitude;
    private String locationMethod;
    private boolean hasLocation;

    // Financial
    private String customerCode;
    private BigDecimal totalPending;
    private boolean hasOutstanding;
    private BigDecimal openingBalance;
    private BigDecimal creditLimit;
    private BigDecimal manualCreditLimit;
    private BigDecimal effectiveCreditLimit;
    private BigDecimal cumulativePaidAmount;
    private long daysActive;
    private boolean autoEligible;
    private boolean isManualOverride;
    private Boolean isNpa;

    // Activity
    private LocalDateTime lastOrderAt;
    private Boolean inactive;

    private Boolean active;
    private LocalDateTime createdAt;
}