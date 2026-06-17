package com.shop.modules.receivables;

import com.shop.modules.billing.Bill;
import com.shop.modules.customer.Customer;
import com.shop.modules.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "udhar_reminder_logs",
    indexes = {
        @Index(name = "idx_reminder_customer", columnList = "customer_id"),
        @Index(name = "idx_reminder_sent_at", columnList = "reminder_sent_at DESC")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UdharReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "reminder_sent_at", nullable = false)
    private LocalDateTime reminderSentAt;

    @Column(nullable = false, length = 20)
    private String channel; // WHATSAPP, MANUAL, SMS, CALL

    @Column(nullable = false, length = 20)
    private String status; // SENT, FAILED, MANUAL

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @PrePersist
    public void prePersist() {
        if (reminderSentAt == null) {
            reminderSentAt = LocalDateTime.now();
        }
    }
}
