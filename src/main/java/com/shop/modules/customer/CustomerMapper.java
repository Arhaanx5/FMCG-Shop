package com.shop.modules.customer;

import com.shop.modules.billing.BillRepository;
import com.shop.modules.customer.dto.CustomerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomerMapper {

    private final BillRepository billRepository;

    public CustomerResponse toResponse(Customer customer) {
        return toResponse(customer, false);
    }

    public CustomerResponse toResponse(Customer customer, boolean includeFinancials) {
        boolean isInactive = customer.getLastOrderAt() != null
                && customer.getLastOrderAt().isBefore(LocalDateTime.now().minusDays(30));

        long daysActive = 0;
        if (customer.getCreatedAt() != null) {
            daysActive = ChronoUnit.DAYS.between(customer.getCreatedAt(), LocalDateTime.now());
        }

        BigDecimal cumulativePaid = BigDecimal.ZERO;
        BigDecimal effectiveLimit = customer.getCreditLimit() != null ? customer.getCreditLimit() : BigDecimal.ZERO;
        boolean autoEligible = false;

        if (includeFinancials) {
            cumulativePaid = billRepository.sumPaidAmountByCustomerId(customer.getId());
            if (cumulativePaid == null) {
                cumulativePaid = BigDecimal.ZERO;
            }
            if (customer.getCreditLimit() != null) {
                effectiveLimit = customer.getCreditLimit();
            } else if (daysActive >= 30 && cumulativePaid.compareTo(new BigDecimal("25000.00")) >= 0) {
                effectiveLimit = new BigDecimal("50000.00");
                autoEligible = true;
            }
        } else {
            if (customer.getCreditLimit() != null) {
                effectiveLimit = customer.getCreditLimit();
            }
        }

        boolean isManualOverride = customer.getCreditLimit() != null;

        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .customerCode(customer.getCustomerCode())
                .shopName(customer.getShopName())
                .phone(customer.getPhone())
                .areaId(customer.getArea() != null ? customer.getArea().getId() : null)
                .areaName(customer.getArea() != null ? customer.getArea().getName() : null)
                .latitude(customer.getLatitude())
                .longitude(customer.getLongitude())
                .locationMethod(customer.getLocationMethod())
                .hasLocation(customer.getLatitude() != null && customer.getLongitude() != null)
                .totalPending(customer.getTotalPending())
                .hasOutstanding(customer.getTotalPending().compareTo(BigDecimal.ZERO) > 0)
                .openingBalance(customer.getOpeningBalance())
                .creditLimit(effectiveLimit)
                .manualCreditLimit(customer.getCreditLimit())
                .effectiveCreditLimit(effectiveLimit)
                .cumulativePaidAmount(cumulativePaid)
                .daysActive(daysActive)
                .autoEligible(autoEligible)
                .isManualOverride(isManualOverride)
                .isNpa(customer.getIsNpa())
                .lastOrderAt(customer.getLastOrderAt())
                .inactive(isInactive)
                .active(customer.getActive())
                .createdAt(customer.getCreatedAt())
                .build();
    }
}
