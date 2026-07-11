package com.shop.modules.customer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.shop.modules.area.Area;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "customers",
    indexes = {
        @Index(name = "idx_customer_active",     columnList = "is_active"),
        @Index(name = "idx_customer_area",       columnList = "area_id"),
        @Index(name = "idx_customer_phone",      columnList = "phone"),
        @Index(name = "idx_customer_last_order", columnList = "last_order_at DESC")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_code", unique = true)
    private String customerCode;

    @Column(nullable = false)
    private String name;

    @Column(name = "shop_name")
    private String shopName;

    private String phone;

    @ManyToOne(fetch = FetchType.LAZY) // LAZY: prevents N+1 — avoids 500 area queries for 500 customers
    @JoinColumn(name = "area_id")
    @JsonIgnore
    @BatchSize(size = 50) // Batch-loads areas: 500 customers = ~10 IN-clause queries instead of 500
    private Area area;

    private Double latitude;
    private Double longitude;

    @Column(name = "location_method")
    private String locationMethod;

    @Column(name = "total_pending")
    @Builder.Default
    private BigDecimal totalPending = BigDecimal.ZERO;

    @Column(name = "credit_limit")
    private BigDecimal creditLimit = null;

    @Column(name = "opening_balance")
    @Builder.Default
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "is_npa")
    @Builder.Default
    private Boolean isNpa = false;

    @Column(name = "last_order_at")
    private LocalDateTime lastOrderAt;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (openingBalance != null) {
            totalPending = totalPending.add(openingBalance);
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}