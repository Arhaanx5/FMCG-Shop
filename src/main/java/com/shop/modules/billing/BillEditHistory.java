package com.shop.modules.billing;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bill_edit_histories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillEditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "bill_number", nullable = false)
    private String billNumber;

    @Column(name = "edited_by", nullable = false)
    private String editedBy;

    @Column(name = "edited_at", nullable = false)
    private LocalDateTime editedAt;

    @Column(name = "old_json", columnDefinition = "TEXT")
    private String oldJson;

    @Column(name = "new_json", columnDefinition = "TEXT")
    private String newJson;

    @Column(columnDefinition = "TEXT")
    private String reason;
}
