package com.companya.customs.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import com.companya.customs.entity.RevokedToken;
import com.companya.customs.repository.RevokedTokenRepository;

@Service
public class JwtService {
    private final SecretKey key; private final long expirationMinutes; private final RevokedTokenRepository revokedTokens;
    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-minutes:480}") long expirationMinutes, RevokedTokenRepository revokedTokens) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        if (expirationMinutes < 1) throw new IllegalStateException("JWT_EXPIRATION_MINUTES must be positive");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.expirationMinutes = expirationMinutes; this.revokedTokens = revokedTokens;
    }
    public String generate(String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder().id(UUID.randomUUID().toString()).subject(email).claim("role", role).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(expirationMinutes * 60))).signWith(key).compact();
    }
    public String extractEmail(String token) { return parse(token).getPayload().getSubject(); }
    public boolean isValid(String token) { try { Claims claims = parse(token).getPayload(); return claims.getId() != null && claims.getExpiration().after(new Date()) && !revokedTokens.existsByTokenIdAndExpiresAtAfter(claims.getId(), java.time.Instant.now()); } catch (JwtException | IllegalArgumentException ex) { return false; } }
    public void revoke(String token) { try { Claims claims = parse(token).getPayload(); if (claims.getId() != null) revokedTokens.save(RevokedToken.builder().tokenId(claims.getId()).expiresAt(claims.getExpiration().toInstant()).build()); } catch (JwtException | IllegalArgumentException ignored) { } }
    private Jws<Claims> parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token); }
}
