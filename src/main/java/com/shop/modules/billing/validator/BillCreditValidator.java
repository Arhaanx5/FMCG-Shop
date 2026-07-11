package com.shop.modules.billing.validator;

import com.shop.modules.billing.PaymentMode;
import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Centralised NPA-block + credit-limit validation for billing operations.
 *
 * <p>Extracted from {@link com.shop.modules.billing.BillService} to eliminate
 * four identical (or near-identical) copy-pasted blocks and to provide a single
 * place to modify NPA/credit-limit business rules going forward.</p>
 *
 * <h3>Usage guide:</h3>
 * <ul>
 *   <li>{@link #validateNpaBlock} – call early in createBill(), before stock is
 *       deducted and before the bill amount is known.</li>
 *   <li>{@link #validateCreditLimit} – call after {@code bill.getPendingAmount()} is
 *       calculated; used in createBill (non-draft), confirmBill, restoreBill.</li>
 *   <li>{@link #validateForUpdate} – call from updateBillDetails(); uses the
 *       pending-amount <em>delta</em> (not the full bill amount) and omits the
 *       PaymentMode guard because the mode is already persisted on the bill.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class BillCreditValidator {

    private final CustomerService customerService;

    // ─────────────────────────────────────────────────────────────────────────
    // Method 1 — NPA block only (no credit-limit check)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Throws if the customer is flagged as NPA and the requested payment mode
     * would extend credit (UDHAR or PARTIAL).
     *
     * <p>Call this <strong>early</strong> in {@code createBill()} — before stock
     * deduction begins — so that the NPA block fires instantly without touching
     * inventory.</p>
     *
     * @param customer    the customer being billed
     * @param paymentMode the payment mode from the bill/request
     */
    public void validateNpaBlock(Customer customer, PaymentMode paymentMode) {
        if (Boolean.TRUE.equals(customer.getIsNpa())) {
            if (paymentMode == PaymentMode.UDHAR || paymentMode == PaymentMode.PARTIAL) {
                throw new RuntimeException(
                        "Credit sales are blocked for NPA customer: "
                        + customer.getName() + " — CASH mode only");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Method 2 — Credit-limit check using full bill pending amount
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Throws if adding {@code billPendingAmount} to the customer's current
     * outstanding balance would exceed their effective credit limit.
     *
     * <p>Call this <strong>after</strong> {@code bill.getPendingAmount()} is
     * calculated.  Used in:
     * <ul>
     *   <li>{@code createBill()} – inside the {@code !isDraft} guard</li>
     *   <li>{@code confirmBill()}</li>
     *   <li>{@code restoreBill()}</li>
     * </ul>
     *
     * @param customer          the customer being billed
     * @param paymentMode       the payment mode on the bill/request
     * @param billPendingAmount the pending (unpaid) amount for this bill
     * @param pendingLabel      context label for the error message
     *                          (e.g. {@code "Requested Credit"} or
     *                          {@code "Restoring Bill Pending"})
     */
    public void validateCreditLimit(
            Customer customer,
            PaymentMode paymentMode,
            BigDecimal billPendingAmount,
            String pendingLabel) {

        if (paymentMode != PaymentMode.UDHAR && paymentMode != PaymentMode.PARTIAL) {
            return; // cash / COD — no credit check needed
        }

        BigDecimal projectedPending = customer.getTotalPending().add(billPendingAmount);
        BigDecimal limit = customerService.calculateEffectiveCreditLimit(customer);

        if (projectedPending.compareTo(limit) > 0) {
            throw new RuntimeException(
                    "Credit limit exceeded for customer: " + customer.getName()
                    + " | Credit Limit: ₹" + limit
                    + " | Current Pending: ₹" + customer.getTotalPending()
                    + " | " + pendingLabel + ": ₹" + billPendingAmount
                    + " | Projected Pending: ₹" + projectedPending);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Method 3 — Update-specific delta-based check
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Update-specific variant of the NPA + credit-limit check.
     *
     * <p>Key differences from {@link #validateCreditLimit}:
     * <ol>
     *   <li>Uses {@code pendingDiff} (the <em>increment</em> in outstanding
     *       balance) rather than the full bill pending amount — because the bill
     *       already existed and only the delta is being added.</li>
     *   <li>The NPA guard has <strong>no PaymentMode check</strong> — any positive
     *       pending increase on an NPA customer is blocked, regardless of mode,
     *       because the payment mode is already persisted and may have changed.</li>
     *   <li>Returns immediately (no-op) when {@code pendingDiff <= 0}.</li>
     * </ol>
     *
     * <p>Used exclusively in {@code updateBillDetails()}.
     *
     * @param customer    the customer associated with the bill being updated
     * @param pendingDiff {@code newPendingAmount - oldPendingAmount}; must be
     *                    positive to trigger any check
     */
    public void validateForUpdate(Customer customer, BigDecimal pendingDiff) {
        if (pendingDiff.compareTo(BigDecimal.ZERO) <= 0) {
            return; // pending not increasing — no credit checks needed
        }

        // NPA block (no PaymentMode guard — delta > 0 is sufficient trigger)
        if (Boolean.TRUE.equals(customer.getIsNpa())) {
            throw new RuntimeException(
                    "Credit sales are blocked for NPA customer: "
                    + customer.getName() + " — CASH mode only");
        }

        // Credit limit check — uses delta, not full pending
        BigDecimal projectedPending = customer.getTotalPending().add(pendingDiff);
        BigDecimal limit = customerService.calculateEffectiveCreditLimit(customer);

        if (projectedPending.compareTo(limit) > 0) {
            throw new RuntimeException(
                    "Credit limit exceeded for customer: " + customer.getName()
                    + " | Credit Limit: ₹" + limit
                    + " | Current Pending: ₹" + customer.getTotalPending()
                    + " | Additional Credit: ₹" + pendingDiff
                    + " | Projected Pending: ₹" + projectedPending);
        }
    }
}
