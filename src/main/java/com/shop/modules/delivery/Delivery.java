package com.shop.modules.delivery;

import com.shop.modules.billing.Bill;
import com.shop.modules.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "deliveries",
    indexes = {
        @Index(name = "idx_delivery_boy_status", columnList = "delivery_boy_id, status"),
        @Index(name = "idx_delivery_created_at", columnList = "created_at DESC")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "bill_id")
    private Bill bill;

    @ManyToOne
    @JoinColumn(name = "delivery_boy_id")
    private User deliveryBoy;

    @ManyToOne
    @JoinColumn(name = "completed_by_id")
    private User completedBy;

    @Enumerated(EnumType.STRING)
    private DeliveryType type;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "cash_collected")
    private BigDecimal cashCollected = BigDecimal.ZERO;

    @Column(name = "first_reminder_sent")
    @Builder.Default
    private Boolean firstReminderSent = false;

    @Column(name = "manager_alerted")
    @Builder.Default
    private Boolean managerAlerted = false;

    @Column(name = "reconciliation_status")
    @Builder.Default
    private String reconciliationStatus = "PENDING";

    @Column(name = "customer_confirmed")
    @Builder.Default
    private Boolean customerConfirmed = false;

    @Column(name = "customer_confirmed_at")
    private LocalDateTime customerConfirmedAt;

    @Column(name = "otp_code")
    private String otpCode;

    @Column(name = "otp_generated_at")
    private LocalDateTime otpGeneratedAt;

    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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