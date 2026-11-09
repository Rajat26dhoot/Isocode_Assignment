package com.companya.customs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "shipments", indexes = @Index(name = "idx_shipment_code", columnList = "shipmentCode", unique = true))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Shipment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String shipmentCode;
    @Column(nullable = false) private String importerName;
    @Convert(converter = com.companya.customs.service.Aes256AttributeConverter.class)
    @Column(length = 2048) private String importerContactEncrypted;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Supplier supplier;
    // Keep this as a VARCHAR instead of a database-native ENUM. Hibernate's schema
    // updater does not reliably add newly introduced enum values to an existing
    // MySQL ENUM column, which otherwise makes creating a shipment fail when the
    // initial status is DOCUMENTS_PENDING.
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32, columnDefinition = "varchar(32)") @Builder.Default private Enums.ShipmentStatus status = Enums.ShipmentStatus.DRAFT;
    @ManyToOne(fetch = FetchType.LAZY) private User assignedExecutive;
    @ManyToOne(fetch = FetchType.LAZY) private User approvedByManager;
    @Builder.Default private Instant createdAt = Instant.now();
    @Builder.Default private Instant updatedAt = Instant.now();
    private Instant approvedAt;
    @Column(length = 2000) private String rejectionReason;
    private Instant submittedAt;
    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
