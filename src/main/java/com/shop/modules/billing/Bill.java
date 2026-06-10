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

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BillStatus status = BillStatus.CONFIRMED;

    private String notes;

    @ManyToOne
    @JoinColumn(name = "created_by")
    @JsonIgnore  // ← never expose user object
    private User createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "bill",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore  // ← use DTO items list instead
    private List<BillItem> items = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}