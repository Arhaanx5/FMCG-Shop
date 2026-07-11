package com.shop.modules.khata;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.customer.Customer;
import com.shop.modules.khata.dto.PaymentResponse;
import com.shop.common.ledger.CustomerLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service responsible for payment cancellations (deletions) and parameter updates.
 */
@Service
@RequiredArgsConstructor
public class PaymentReversalService {

    private final PaymentRepository paymentRepository;
    private final BillRepository billRepository;
    private final CustomerLedgerService customerLedgerService;
    private final KhataMapper khataMapper;

    /**
     * Deletes a payment record and reverses its allocations from target bills and customer ledger.
     */
    @Transactional
    public void deletePayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));

        Customer customer = payment.getCustomer();
        AdjustmentType type = payment.getAdjustmentType() != null
                ? payment.getAdjustmentType() : AdjustmentType.NORMAL;

        // Reverse source bill
        Bill bill = payment.getBill();
        if (bill != null) {
            BigDecimal appliedToSource = payment.getAppliedAmount() != null
                    ? payment.getAppliedAmount() : payment.getAmount();
            BigDecimal newPaid = bill.getPaidAmount().subtract(appliedToSource);
            bill.setPaidAmount(newPaid.compareTo(BigDecimal.ZERO) < 0
                    ? BigDecimal.ZERO : newPaid);
            bill.setPendingAmount(bill.getGrandTotal().subtract(bill.getPaidAmount()));
            bill.setStatus(khataMapper.deriveBillStatus(bill));
            billRepository.save(bill);
        }

        // Reverse adjusted bill (MANUAL or AUTO)
        if ((type == AdjustmentType.MANUAL_ADJUST || type == AdjustmentType.AUTO_ADJUST)
                && payment.getAdjustedBillId() != null) {
            billRepository.findById(payment.getAdjustedBillId()).ifPresent(adjBill -> {
                BigDecimal excess = payment.getExcessAmount() != null
                        ? payment.getExcessAmount() : BigDecimal.ZERO;
                BigDecimal newPaid = adjBill.getPaidAmount().subtract(excess);
                adjBill.setPaidAmount(newPaid.compareTo(BigDecimal.ZERO) < 0
                        ? BigDecimal.ZERO : newPaid);
                adjBill.setPendingAmount(adjBill.getGrandTotal().subtract(adjBill.getPaidAmount()));
                adjBill.setStatus(khataMapper.deriveBillStatus(adjBill));
                billRepository.save(adjBill);
            });
        }

        paymentRepository.delete(payment);
        customerLedgerService.recalculateCustomerPending(customer);
    }

    /**
     * Updates payment details (e.g. paymentMode, notes) and validates waive-off conditions.
     */
    @Transactional
    public PaymentResponse updatePayment(UUID id, String paymentMode, String notes) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));
        if (paymentMode != null) {
            if ("WAIVE_OFF".equalsIgnoreCase(paymentMode)) {
                if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new RuntimeException("Waive-off amount zero ya negative nahi ho sakta.");
                }
                if (payment.getAmount().compareTo(new BigDecimal("200")) > 0) {
                    throw new RuntimeException("Waive-off ₹200 se zyada nahi ho sakta. Entered: ₹" + String.format("%.2f", payment.getAmount()));
                }
            }
            payment.setPaymentMode(paymentMode);
        }
        if (notes != null) payment.setNotes(notes);
        return khataMapper.toResponse(paymentRepository.save(payment));
    }
}
