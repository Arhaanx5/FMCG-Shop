package com.shop.modules.khata;

import com.shop.modules.billing.Bill;
import com.shop.modules.customer.Customer;
import com.shop.modules.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_collected_by_paid_at", columnList = "collected_by, paid_at DESC")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "bill_id")
    private Bill bill;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** Total amount the user typed in — the face value of this payment record */
    private BigDecimal amount;

    /** Portion of `amount` actually applied to `bill` */
    @Column(name = "applied_amount")
    @Builder.Default
    private BigDecimal appliedAmount = BigDecimal.ZERO;

    /** Portion of `amount` that exceeded bill's pending and was redirected */
    @Column(name = "excess_amount")
    @Builder.Default
    private BigDecimal excessAmount = BigDecimal.ZERO;

    /** The OTHER bill that received the excess (nullable for NORMAL payments) */
    @Column(name = "adjusted_bill_id")
    private UUID adjustedBillId;

    /** How was the excess handled */
    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type")
    @Builder.Default
    private AdjustmentType adjustmentType = AdjustmentType.NORMAL;

    /** Human-readable summary, e.g. "Excess ₹50 auto-adjusted to BILL-00023" */
    @Column(name = "adjustment_note")
    private String adjustmentNote;

    @Column(name = "payment_mode")
    private String paymentMode;

    private String notes;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @ManyToOne
    @JoinColumn(name = "collected_by")
    private User collectedBy;

    @PrePersist
    public void prePersist() {
        paidAt = LocalDateTime.now();
        if (appliedAmount == null) appliedAmount = BigDecimal.ZERO;
        if (excessAmount == null) excessAmount = BigDecimal.ZERO;
        if (adjustmentType == null) adjustmentType = AdjustmentType.NORMAL;
    }
}