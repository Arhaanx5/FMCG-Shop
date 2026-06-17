package com.shop.modules.dashboard;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "health_reports",
    uniqueConstraints = @UniqueConstraint(columnNames = {"report_year", "report_month"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "report_year", nullable = false)
    private int reportYear;

    @Column(name = "report_month", nullable = false)
    private int reportMonth;

    @Column(name = "report_json", columnDefinition = "TEXT", nullable = false)
    private String reportJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
