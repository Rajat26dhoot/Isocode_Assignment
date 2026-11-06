package com.companya.customs.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "suppliers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Supplier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String country;
    @Builder.Default @Column(nullable = false) private long totalFieldsExtracted = 0;
    @Builder.Default @Column(nullable = false) private long totalFieldsCorrected = 0;
}
