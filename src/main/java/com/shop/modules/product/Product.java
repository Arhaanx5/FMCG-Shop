package com.shop.modules.product;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
@BatchSize(size = 50) // Batch-loads Product proxies in one IN-clause query (prevents N+1 from BillItem.product)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_code", unique = true)
    private String productCode;

    @Column(nullable = false)
    private String name;

    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(name = "other_category_detail")
    private String otherCategoryDetail;

    // GST — 0 to 40%
    @Column(name = "gst_percent")
    private BigDecimal gstPercent = BigDecimal.ZERO;

    // Compensation Cess % (optional, e.g. for soft drinks)
    @Column(name = "cess_percent")
    private BigDecimal cessPercent = BigDecimal.ZERO;

    // Primary unit — BOX / CRATE / CARTON
    @Column(name = "primary_unit")
    private String primaryUnit;

    // Secondary unit — LADI / BOTTLE / PACK
    @Column(name = "secondary_unit")
    private String secondaryUnit;

    // How many secondary per primary
    // e.g. 12 LADI per BOX
    // e.g. 24 BOTTLE per CRATE
    @Column(name = "secondary_per_primary")
    private Integer secondaryPerPrimary = 1;

    // Can sell by primary unit (BOX/CRATE)
    @Column(name = "can_sell_primary")
    private Boolean canSellPrimary = true;

    // Can sell by secondary unit (LADI/BOTTLE)
    @Column(name = "can_sell_secondary")
    private Boolean canSellSecondary = true;

    // Buy price — WITHOUT tax (per primary unit)
    @Column(name = "buy_price_without_tax")
    private BigDecimal buyPriceWithoutTax = BigDecimal.ZERO;

    // Buy price — WITH tax (auto calculated)
    @Column(name = "buy_price_with_tax")
    private BigDecimal buyPriceWithTax = BigDecimal.ZERO;

    // Sell price per primary unit (BOX/CRATE)
    @Column(name = "sell_price_primary")
    private BigDecimal sellPricePrimary = BigDecimal.ZERO;

    // Sell price per secondary unit (LADI/BOTTLE)
    @Column(name = "sell_price_secondary")
    private BigDecimal sellPriceSecondary = BigDecimal.ZERO;

    // Low stock alert threshold
    @Column(name = "low_stock_alert")
    private Integer lowStockAlert = 10;

    // Low stock unit — PRIMARY or SECONDARY
    @Column(name = "low_stock_unit")
    private String lowStockUnit = "SECONDARY";

    @Column(name = "is_active")
    private Boolean active = true;

    @Column(name = "hsn_code")
    private String hsnCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        // Auto calculate buy price with tax
        calculateBuyPriceWithTax();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        calculateBuyPriceWithTax();
    }

    // Auto calculate buy price with tax
    public void calculateBuyPriceWithTax() {
        if (buyPriceWithoutTax != null) {
            BigDecimal gstAmount = BigDecimal.ZERO;
            if (gstPercent != null) {
                gstAmount = buyPriceWithoutTax
                        .multiply(gstPercent)
                        .divide(BigDecimal.valueOf(100));
            }
            BigDecimal cessAmount = BigDecimal.ZERO;
            if (cessPercent != null) {
                cessAmount = buyPriceWithoutTax
                        .multiply(cessPercent)
                        .divide(BigDecimal.valueOf(100));
            }
            buyPriceWithTax = buyPriceWithoutTax.add(gstAmount).add(cessAmount);
        }
    }

    // Get buy price per secondary unit
    public BigDecimal getBuyPricePerSecondary() {
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

    public String getPrimaryUnit() {
        if (primaryUnit == null) return null;
        String u = primaryUnit.toUpperCase().trim();
        if (u.equals("PACKET") || u.equals("PACKETS")) return "PACK";
        return u;
    }

    public void setPrimaryUnit(String primaryUnit) {
        if (primaryUnit != null) {
            String u = primaryUnit.toUpperCase().trim();
            if (u.equals("PACKET") || u.equals("PACKETS")) {
                this.primaryUnit = "PACK";
                return;
            }
            this.primaryUnit = u;
        } else {
            this.primaryUnit = null;
        }
    }

    public String getSecondaryUnit() {
        if (secondaryUnit == null) return null;
        String u = secondaryUnit.toUpperCase().trim();
        if (u.equals("PACKET") || u.equals("PACKETS")) return "PACK";
        return u;
    }

    public void setSecondaryUnit(String secondaryUnit) {
        if (secondaryUnit != null) {
            String u = secondaryUnit.toUpperCase().trim();
            if (u.equals("PACKET") || u.equals("PACKETS")) {
                this.secondaryUnit = "PACK";
                return;
            }
            this.secondaryUnit = u;
        } else {
            this.secondaryUnit = null;
        }
    }

    public int getLowStockAlertInSecondary() {
        int alert = lowStockAlert != null ? lowStockAlert : 10;
        if ("PRIMARY".equalsIgnoreCase(lowStockUnit)) {
            int ratio = secondaryPerPrimary != null ? secondaryPerPrimary : 1;
            return alert * ratio;
        }
        return alert;
    }

    public int getReorderLevel() {
        return getLowStockAlertInSecondary();
    }
}