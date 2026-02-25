package uk.co.quietadmin.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<uk.co.quietadmin.domain.auth.PasswordResetToken> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
            String tokenHash,
            Instant now
    );

    void deleteAllByUserId(Long id);
}