package com.companya.customs.repository;

import com.companya.customs.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
    boolean existsByTokenIdAndExpiresAtAfter(String tokenId, Instant now);
}
