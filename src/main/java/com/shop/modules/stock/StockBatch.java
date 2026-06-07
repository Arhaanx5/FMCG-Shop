package com.shop.modules.stock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.shop.modules.product.Product;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_batches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "product_id")
    @JsonIgnore
    private Product product;

    @Column(name = "batch_number")
    private String batchNumber;

    // Primary units received (BOX/CRATE/CARTON)
    @Column(name = "primary_received")
    @Builder.Default
    private Integer primaryReceived = 0;

    // Secondary units received (LADI/BOTTLE/PACK)
    // auto calculated from primary × secondaryPerPrimary
    @Column(name = "secondary_received")
    @Builder.Default
    private Integer secondaryReceived = 0;

    // Secondary units remaining (for FIFO)
    @Column(name = "secondary_remaining")
    @Builder.Default
    private Integer secondaryRemaining = 0;

    // Secondary units soft reserved for draft bookings
    @Column(name = "secondary_soft_reserved")
    @Builder.Default
    private Integer secondarySoftReserved = 0;

    // Buy price without tax per PRIMARY unit
    @Column(name = "buy_price_without_tax")
    private BigDecimal buyPriceWithoutTax;

    // Buy price with tax per PRIMARY unit
    @Column(name = "buy_price_with_tax")
    private BigDecimal buyPriceWithTax;

    // GST percent at time of purchase
    @Column(name = "gst_percent")
    @Builder.Default
    private BigDecimal gstPercent = BigDecimal.ZERO;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "is_exhausted")
    @Builder.Default
    private Boolean exhausted = false;

    @PrePersist
    public void prePersist() {
        receivedAt = LocalDateTime.now();
    }

    // Get buy price per secondary unit
    public BigDecimal getBuyPricePerSecondary(
            Integer secondaryPerPrimary) {
        if (buyPriceWithoutTax == null
                || secondaryPerPrimary == null
                || secondaryPerPrimary == 0) {
            return BigDecimal.ZERO;
        }
        return buyPriceWithoutTax.divide(
                BigDecimal.valueOf(secondaryPerPrimary),
                2,
                java.math.RoundingMode.HALF_UP);
    }
}