package com.shop.modules.billing;

import com.shop.modules.product.Product;
import com.shop.modules.product.UnitType;
import com.shop.modules.stock.StockBatch;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonBackReference;


@Entity
@Table(name = "bill_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "bill_id")
    @JsonBackReference
    private Bill bill;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne
    @JoinColumn(name = "batch_id")
    private StockBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type")
    private UnitType unitType;

    private int quantity;

    @Column(name = "free_quantity")
    @Builder.Default
    private int freeQuantity = 0;

    private BigDecimal rate;

    @Column(name = "gst_percent")
    @Builder.Default
    private BigDecimal gstPercent = BigDecimal.ZERO;

    @Column(name = "gst_amount")
    @Builder.Default
    private BigDecimal gstAmount = BigDecimal.ZERO;

    @Column(name = "cess_percent")
    @Builder.Default
    private BigDecimal cessPercent = BigDecimal.ZERO;

    @Column(name = "cess_amount")
    @Builder.Default
    private BigDecimal cessAmount = BigDecimal.ZERO;

    private BigDecimal total;

    @Column(name = "is_offer")
    @Builder.Default
    private Boolean offer = false;
}