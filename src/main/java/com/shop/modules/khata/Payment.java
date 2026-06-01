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

    private BigDecimal amount;

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
    }
}