package com.shop.modules.shopprofile;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shop_profile")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopProfile {

    @Id
    private UUID id;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String gstin;

    private String fssai;

    private String phone;

    private String address;

    @Column(name = "state_code", nullable = false)
    private String stateCode;

    @Column(name = "state_name", nullable = false)
    private String stateName;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    @PrePersist
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
