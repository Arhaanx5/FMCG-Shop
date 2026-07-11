package com.shop.modules.khata;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.khata.dto.OverpaymentPreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service responsible for computing and previewing overpayment distributions.
 */
@Service
@RequiredArgsConstructor
public class PaymentPreviewService {

    private final BillRepository billRepository;

    /**
     * Returns an OverpaymentPreviewResponse when amount > bill.pendingAmount.
     * Returns null if amount is within the pending amount (normal payment).
     */
    public OverpaymentPreviewResponse previewOverpayment(
            UUID customerId, UUID billId, BigDecimal amount) {

        if (billId == null) return null; // No specific bill → existing FIFO handles it

        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        BigDecimal pending = bill.getPendingAmount();
        if (amount.compareTo(pending) <= 0) return null; // Normal, no overpayment

        BigDecimal excess = amount.subtract(pending);

        // Get other pending bills for FIFO preview
        List<Bill> otherBills = billRepository
                .findPendingBillsForCustomerExcluding(customerId, billId);

        List<OverpaymentPreviewResponse.BillSummary> summaries = otherBills.stream()
                .map(b -> OverpaymentPreviewResponse.BillSummary.builder()
                        .billId(b.getId())
                        .billNumber(b.getBillNumber())
                        .pendingAmount(b.getPendingAmount())
                        .grandTotal(b.getGrandTotal())
                        .createdAt(b.getCreatedAt() != null ? b.getCreatedAt().toString() : null)
                        .build())
                .collect(Collectors.toList());

        // Compute FIFO auto-distribution preview
        List<OverpaymentPreviewResponse.AutoDistributionEntry> autoEntries = new ArrayList<>();
        BigDecimal remaining = excess;
        for (Bill ob : otherBills) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal apply = remaining.min(ob.getPendingAmount());
            autoEntries.add(OverpaymentPreviewResponse.AutoDistributionEntry.builder()
                    .billId(ob.getId())
                    .billNumber(ob.getBillNumber())
                    .pendingBefore(ob.getPendingAmount())
                    .amountApplied(apply)
                    .pendingAfter(ob.getPendingAmount().subtract(apply))
                    .willBeFullyPaid(ob.getPendingAmount().subtract(apply)
                            .compareTo(BigDecimal.ZERO) <= 0)
                    .build());
            remaining = remaining.subtract(apply);
        }

        return OverpaymentPreviewResponse.builder()
                .sourceBillId(bill.getId())
                .sourceBillNumber(bill.getBillNumber())
                .sourceBillPending(pending)
                .paymentAmount(amount)
                .excessAmount(excess)
                .otherPendingBills(summaries)
                .autoDistribution(autoEntries)
                .remainingAfterAuto(remaining) // > 0 means will block
                .build();
    }
}
