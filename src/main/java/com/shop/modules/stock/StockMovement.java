package com.shop.modules.stock;

import com.shop.modules.product.Product;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private StockBatch batch;

    @Column(name = "movement_type", nullable = false, length = 50)
    private String movementType; // PURCHASE, SALE, RETURN_IN, RETURN_OUT, DAMAGE, EXPIRY, ADJUSTMENT, TRANSFER, OPENING_STOCK

    @Column(name = "quantity", nullable = false)
    private Integer quantity; // in secondary units

    @Column(name = "quantity_before")
    private Integer quantityBefore;

    @Column(name = "quantity_after")
    private Integer quantityAfter;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "total_value")
    private BigDecimal totalValue;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "supplier_invoice_date")
    private LocalDate supplierInvoiceDate;

    @Column(name = "supplier_name", length = 255)
    private String supplierName;

    @Column(name = "buy_price_without_tax")
    private BigDecimal buyPriceWithoutTax;

    @Column(name = "buy_price_with_tax")
    private BigDecimal buyPriceWithTax;

    @Column(name = "gst_percent")
    private BigDecimal gstPercent;

    @Column(name = "secondary_per_primary")
    private Integer secondaryPerPrimary;

    @Column(name = "receive_source", length = 50)
    private String receiveSource;

    @PrePersist
    public void prePersist() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
