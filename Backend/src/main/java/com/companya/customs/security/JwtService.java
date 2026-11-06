package com.companya.customs.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key; private final long expirationMinutes;
    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-minutes:480}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.expirationMinutes = expirationMinutes;
    }
    public String generate(String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder().subject(email).claim("role", role).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(expirationMinutes * 60))).signWith(key).compact();
    }
    public String extractEmail(String token) { return parse(token).getPayload().getSubject(); }
    public boolean isValid(String token) { try { return parse(token).getPayload().getExpiration().after(new Date()); } catch (JwtException | IllegalArgumentException ex) { return false; } }
    private Jws<Claims> parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token); }
}
