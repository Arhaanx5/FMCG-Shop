package com.shop.modules.hsnmapping;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "hsn_category_mapping",
    uniqueConstraints = @UniqueConstraint(columnNames = "category_key")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HsnCategoryMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "category_key", nullable = false, unique = true)
    private String categoryKey;

    @Column(name = "hsn_code", nullable = false)
    private String hsnCode;

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
