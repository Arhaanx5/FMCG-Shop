package com.shop.modules.delivery;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cod_audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CODAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "delivery_boy_id", nullable = false)
    private UUID deliveryBoyId;

    @Column(nullable = false)
    private String event;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "old_mode")
    private String oldMode;

    @Column(name = "new_mode")
    private String newMode;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "device_info")
    private String deviceInfo;

    @PrePersist
    public void prePersist() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
