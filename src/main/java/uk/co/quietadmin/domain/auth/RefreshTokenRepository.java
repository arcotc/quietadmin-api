package uk.co.quietadmin.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);
    void deleteByUserId(Long userId);
    Optional<RefreshToken> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
            String tokenHash,
            Instant now
    );
}