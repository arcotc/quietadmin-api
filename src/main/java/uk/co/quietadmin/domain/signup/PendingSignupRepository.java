package uk.co.quietadmin.domain.signup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PendingSignupRepository extends JpaRepository<PendingSignup, Long> {
    Optional<PendingSignup> findByToken(String token);
    void deleteByExpiresAtBefore(Instant cutoff);
}