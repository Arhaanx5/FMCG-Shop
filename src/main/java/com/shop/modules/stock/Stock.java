package com.shop.modules.stock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.shop.modules.product.Product;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "product_id", unique = true)
//    @JsonIgnore
    private Product product;

    // PRIMARY unit tracking (BOX/CRATE/CARTON)
    // Only sealed/complete primary units
    @Column(name = "total_primary_units")
    private Integer totalPrimaryUnits = 0;

    // Helper — get total secondary including open
    // SECONDARY unit tracking (LADI/BOTTLE/PACK)
    // ALL secondary units including open box
    @Column(name = "total_secondary_units")
    private Integer totalSecondaryUnits = 0;

    // Open primary unit tracking
    // e.g. 1 box opened — how many ladis left in it
    @Column(name = "open_primary_remaining")
    private Integer openPrimaryRemaining = 0;

    // Is there an open primary unit right now
    @Column(name = "has_open_primary")
    private Boolean hasOpenPrimary = false;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        lastUpdated = LocalDateTime.now();
    }

    // Helper — get sealed primary units only
    public Integer getSealedPrimaryUnits() {
        return totalPrimaryUnits;
    }

}