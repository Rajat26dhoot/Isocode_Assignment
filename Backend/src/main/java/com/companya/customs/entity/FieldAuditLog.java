package com.companya.customs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "field_audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FieldAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Shipment shipment;
    private Long extractedFieldId;
    private Long documentId;
    @Column(length = 128) private String fieldName;
    @Column(length = 32) @Builder.Default private String actionType = "MANUAL_CORRECTION";
    @Enumerated(EnumType.STRING) private Enums.FieldKey fieldKey;
    @Column(length = 4000) private String oldValue;
    @Column(length = 4000) private String newValue;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User changedByUser;
    @Column(nullable = false, length = 1000) private String changeReason;
    @Builder.Default private Instant changedAt = Instant.now();
}
