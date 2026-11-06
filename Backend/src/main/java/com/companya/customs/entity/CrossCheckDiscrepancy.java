package com.companya.customs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "cross_check_discrepancies", indexes = @Index(name = "idx_discrepancy_shipment_rule", columnList = "shipment_id,ruleCode"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CrossCheckDiscrepancy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Shipment shipment;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Enums.RuleCode ruleCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Enums.Severity severity;
    // These values can contain OCR output and reconciliation details. Keep them off-row
    // so MySQL/InnoDB does not reject the table because of the 65,535-byte row limit.
    @Lob private String invoiceValue;
    @Lob private String packingListValue;
    @Lob private String bolValue;
    @Lob private String expectedOrDelta;
    @Enumerated(EnumType.STRING) @Column(nullable = false) @Builder.Default private Enums.DiscrepancyStatus status = Enums.DiscrepancyStatus.OPEN;
    @Lob private String resolutionNote;
    @ManyToOne(fetch = FetchType.LAZY) private User resolvedByUser;
    private Instant resolvedAt;
}
