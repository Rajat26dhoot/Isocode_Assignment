package com.companya.customs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "line_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LineItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private ShipmentDocument document;
    private Integer itemNumber;
    @Column(length = 1000) private String description;
    private String hsnCode;
    @Column(precision = 18, scale = 3) private BigDecimal quantity;
    @Column(precision = 18, scale = 4) private BigDecimal unitPrice;
    private String currency;
    @Column(precision = 18, scale = 2) private BigDecimal lineTotal;
    @Column(precision = 18, scale = 3) private BigDecimal grossWeightKg;
    @Column(precision = 4, scale = 3) private BigDecimal confidenceScore;
    @Builder.Default private boolean isHumanVerified = false;
}
