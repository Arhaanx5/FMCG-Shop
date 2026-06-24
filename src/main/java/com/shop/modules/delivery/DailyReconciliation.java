package com.shop.modules.delivery;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_reconciliations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyReconciliation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "delivery_boy_id", nullable = false)
    private UUID deliveryBoyId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "expected_collection", nullable = false)
    private BigDecimal expectedCollection;

    @Column(name = "submitted_collection", nullable = false)
    private BigDecimal submittedCollection;

    @Column(nullable = false)
    private BigDecimal gap;

    @Builder.Default
    private String status = "PENDING"; // PENDING, APPROVED, FLAGGED

    @Column(name = "admin_notes", length = 500)
    private String adminNotes;
}
