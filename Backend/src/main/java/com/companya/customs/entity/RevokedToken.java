package com.companya.customs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "revoked_jwt_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RevokedToken {
    @Id @Column(length = 64) private String tokenId;
    @Column(nullable = false) private Instant expiresAt;
}
