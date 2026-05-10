package uk.co.quietadmin.api.rota;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RotaDeclineTokenRepository extends JpaRepository<RotaDeclineToken, Long> {
    Optional<RotaDeclineToken> findByTokenHashAndUsedAtIsNull(String tokenHash);
}
