package com.shop.modules.customer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.shop.modules.area.Area;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customers")
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

    @ManyToOne
    @JoinColumn(name = "area_id")
    @JsonIgnore
    private Area area;

    private Double latitude;
    private Double longitude;

    @Column(name = "location_method")
    private String locationMethod;

    @Column(name = "total_pending")
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