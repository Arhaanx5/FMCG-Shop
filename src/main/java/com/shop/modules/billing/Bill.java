package com.shop.modules.billing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.shop.modules.customer.Customer;
import com.shop.modules.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "bills",
    indexes = {
        @Index(name = "idx_bill_customer_created_at", columnList = "customer_id, created_at DESC"),
        @Index(name = "idx_bill_created_at", columnList = "created_at DESC")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bill_number", unique = true)
    private String billNumber;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    @JsonIgnore  // ← use DTO fields instead
    private Customer customer;

    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "gst_total")
    @Builder.Default
    private BigDecimal gstTotal = BigDecimal.ZERO;

    @Column(name = "cess_total")
    @Builder.Default
    private BigDecimal cessTotal = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "grand_total")
    @Builder.Default
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "paid_amount")
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "pending_amount")
    @Builder.Default
    private BigDecimal pendingAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode")
    private PaymentMode paymentMode;

    @Column(name = "partial_payment_mode")
    private String partialPaymentMode;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BillStatus status = BillStatus.CONFIRMED;

    @Transient
    private boolean forceStatusChange;

    public void setStatus(BillStatus status) {
        if (!this.forceStatusChange && (this.status == BillStatus.COD_COLLECTED || this.status == BillStatus.PAID)) {
            if (status == BillStatus.COD_PENDING || status == BillStatus.COD_DELIVERED) {
                throw new RuntimeException("Invalid status transition: Cannot revert a paid/collected bill back to pending");
            }
        }
        this.status = status;
    }

    private String notes;

    @ManyToOne
    @JoinColumn(name = "created_by")
    @JsonIgnore  // ← never expose user object
    private User createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Builder.Default
    private Integer version = 0;

    @OneToMany(mappedBy = "bill",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore  // ← use DTO items list instead
    private List<BillItem> items = new ArrayList<>();

    public void validateTotals() {
        if (status == BillStatus.CANCELLED || status == BillStatus.DRAFT) {
            return;
        }

        BigDecimal sub = subtotal != null ? subtotal : BigDecimal.ZERO;
        BigDecimal gst = gstTotal != null ? gstTotal : BigDecimal.ZERO;
        BigDecimal cess = cessTotal != null ? cessTotal : BigDecimal.ZERO;
        BigDecimal disc = discount != null ? discount : BigDecimal.ZERO;
        BigDecimal grand = grandTotal != null ? grandTotal : BigDecimal.ZERO;

        BigDecimal expectedGrand = sub.add(gst).add(cess).subtract(disc);
        if (grand.subtract(expectedGrand).abs().compareTo(new BigDecimal("0.05")) > 0) {
            throw new RuntimeException("Bill total mismatch: grandTotal=" + grand + " but expected=" + expectedGrand);
        }

        if (items != null && !items.isEmpty()) {
            BigDecimal itemsSum = BigDecimal.ZERO;
            for (BillItem item : items) {
                if (item.getTotal() != null) {
                    itemsSum = itemsSum.add(item.getTotal());
                }
            }
            BigDecimal expectedItemsSum = grand.add(disc);
            if (itemsSum.subtract(expectedItemsSum).abs().compareTo(new BigDecimal("0.05")) > 0) {
                throw new RuntimeException("Bill item total mismatch: sum of items=" + itemsSum + " but expected (grandTotal + discount)=" + expectedItemsSum);
            }
        }
    }

    @PrePersist
    public void prePersist() {
        validateTotals();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        version = 0;
    }

    @PreUpdate
    public void preUpdate() {
        validateTotals();
        updatedAt = LocalDateTime.now();
    }
}