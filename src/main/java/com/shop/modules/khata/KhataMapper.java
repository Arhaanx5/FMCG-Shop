package com.shop.modules.khata;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.khata.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Component responsible for mapping Payment entities to DTO responses
 * and managing transitional calculations for bills.
 */
@Component
@RequiredArgsConstructor
public class KhataMapper {

    private final BillRepository billRepository;

    /**
     * Converts a single Payment entity to a PaymentResponse DTO.
     */
    public PaymentResponse toResponse(Payment payment) {
        String adjustedBillNumber = null;
        if (payment.getAdjustedBillId() != null) {
            adjustedBillNumber = billRepository.findById(payment.getAdjustedBillId())
                    .map(Bill::getBillNumber).orElse(null);
        }
        return mapToDto(payment, adjustedBillNumber);
    }

    /**
     * Converts a list of Payment entities to a list of PaymentResponse DTOs in bulk,
     * resolving adjusted bill numbers via a single database query.
     */
    public List<PaymentResponse> toResponses(List<Payment> payments) {
        List<UUID> adjustedBillIds = payments.stream()
                .map(Payment::getAdjustedBillId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<UUID, String> billNumMap = new java.util.HashMap<>();
        if (!adjustedBillIds.isEmpty()) {
            List<Bill> bills = billRepository.findAllById(adjustedBillIds);
            billNumMap = bills.stream()
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toMap(Bill::getId, Bill::getBillNumber, (a, b) -> a));
        }

        final Map<UUID, String> finalBillNumMap = billNumMap;
        return payments.stream()
                .map(p -> {
                    String adjustedBillNumber = p.getAdjustedBillId() != null 
                            ? finalBillNumMap.get(p.getAdjustedBillId()) 
                            : null;
                    return mapToDto(p, adjustedBillNumber);
                })
                .collect(Collectors.toList());
    }

    /**
     * Update a bill's paidAmount and pendingAmount, and save the updated entity.
     */
    public void applyPaymentToBill(Bill bill, BigDecimal amountToApply) {
        bill.setPaidAmount(bill.getPaidAmount().add(amountToApply));
        BigDecimal newPending = bill.getGrandTotal().subtract(bill.getPaidAmount());
        bill.setPendingAmount(newPending.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO : newPending);
        bill.setStatus(deriveBillStatus(bill));
        billRepository.save(bill);
    }

    /**
     * Derives correct BillStatus based on bill payment details.
     */
    public BillStatus deriveBillStatus(Bill bill) {
        if (bill.getPaymentMode() == com.shop.modules.billing.PaymentMode.COD) {
            if (bill.getPaidAmount().compareTo(BigDecimal.ZERO) == 0) {
                return BillStatus.COD_PENDING;
            } else if (bill.getPendingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return BillStatus.COD_COLLECTED;
            } else {
                return BillStatus.PARTIAL;
            }
        } else {
            if (bill.getPaidAmount().compareTo(BigDecimal.ZERO) == 0) {
                return BillStatus.CONFIRMED;
            } else if (bill.getPendingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return BillStatus.PAID;
            } else {
                return BillStatus.PARTIAL;
            }
        }
    }

    private PaymentResponse mapToDto(Payment payment, String adjustedBillNumber) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .customerId(payment.getCustomer().getId())
                .customerName(payment.getCustomer().getName())
                .customerShopName(payment.getCustomer().getShopName())
                .billId(payment.getBill() != null ? payment.getBill().getId() : null)
                .billNumber(payment.getBill() != null ? payment.getBill().getBillNumber() : null)
                .amount(payment.getAmount())
                .appliedAmount(payment.getAppliedAmount())
                .excessAmount(payment.getExcessAmount())
                .adjustmentType(payment.getAdjustmentType() != null
                        ? payment.getAdjustmentType().name() : null)
                .adjustmentNote(payment.getAdjustmentNote())
                .adjustedBillId(payment.getAdjustedBillId())
                .adjustedBillNumber(adjustedBillNumber)
                .paymentMode(payment.getPaymentMode())
                .paymentSource(payment.getPaymentSource())
                .notes(payment.getNotes())
                .paidAt(payment.getPaidAt())
                .collectedBy(payment.getCollectedBy() != null
                        ? payment.getCollectedBy().getName() : null)
                .customerPendingBalance(payment.getCustomer().getTotalPending())
                .billGrandTotal(payment.getBill() != null ? payment.getBill().getGrandTotal() : null)
                .billPendingAmount(payment.getBill() != null ? payment.getBill().getPendingAmount() : null)
                .build();
    }
}
