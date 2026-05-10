package uk.co.quietadmin.api.rota;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RotaUserDeclineTokenRepository extends JpaRepository<RotaUserDeclineToken, Long> {
    Optional<RotaUserDeclineToken> findByTokenHashAndUsedAtIsNull(String tokenHash);
}
