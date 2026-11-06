package com.companya.customs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "extracted_fields", indexes = @Index(name = "idx_field_document_key", columnList = "document_id,fieldKey"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExtractedField {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private ShipmentDocument document;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 64) private Enums.FieldKey fieldKey;
    @Column(nullable = false) private String fieldLabel;
    @Column(length = 4000) private String aiExtractedValue;
    @Column(length = 4000) private String currentValue;
    @Column(precision = 4, scale = 3, nullable = false) private BigDecimal confidenceScore;
    @Builder.Default @Column(nullable = false) private boolean isLowConfidence = false;
    @Builder.Default @Column(nullable = false) private boolean isHumanVerified = false;
    @Builder.Default @Column(nullable = false) private boolean wasEdited = false;
    private Integer pageNumber;
    @ManyToOne(fetch = FetchType.LAZY) private User verifiedByUser;
    private Instant verifiedAt;
}
