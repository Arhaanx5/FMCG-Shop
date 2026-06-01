package com.shop.modules.damage;

import com.shop.modules.product.Product;
import com.shop.modules.product.UnitType;
import com.shop.modules.stock.StockBatch;
import com.shop.modules.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "damage_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DamageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne
    @JoinColumn(name = "batch_id")
    private StockBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type")
    private UnitType unitType;

    private Integer quantity;

    @Enumerated(EnumType.STRING)
    private DamageReason reason;

    @Column(name = "value_loss")
    private BigDecimal valueLoss = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_level")
    private UnitLevel unitLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_status")
    private ClaimStatus claimStatus;

    private String notes;

    @ManyToOne
    @JoinColumn(name = "logged_by")
    private User loggedBy;

    @Column(name = "logged_at")
    private LocalDateTime loggedAt;

    @PrePersist
    public void prePersist() {
        loggedAt = LocalDateTime.now();
    }
}