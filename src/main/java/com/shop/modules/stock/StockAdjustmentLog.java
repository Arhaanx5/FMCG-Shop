package com.shop.modules.stock;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_adjustment_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustmentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "batch_number", nullable = false)
    private String batchNumber;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "old_secondary_remaining", nullable = false)
    private Integer oldSecondaryRemaining;

    @Column(name = "new_secondary_remaining", nullable = false)
    private Integer newSecondaryRemaining;

    @Column(name = "adjusted_by", nullable = false)
    private String adjustedBy;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime timestamp;
}
