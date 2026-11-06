package com.companya.customs.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "shipment_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShipmentDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Shipment shipment;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Enums.DocumentType docType;
    @Column(nullable = false) private String fileName;
    private String fileUrl;
    @Lob @Column(columnDefinition = "LONGBLOB") private byte[] fileContent;
    private String contentType;
    private Integer pageCount;
    @Builder.Default @Column(nullable = false) private boolean isUnreadable = false;
    @Enumerated(EnumType.STRING) @Column(nullable = false) @Builder.Default private Enums.ExtractionStatus extractionStatus = Enums.ExtractionStatus.PENDING;
    @Builder.Default private boolean humanVerified = false;
}
