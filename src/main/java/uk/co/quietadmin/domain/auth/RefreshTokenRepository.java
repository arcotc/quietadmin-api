package uk.co.quietadmin.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    @Modifying
    @Query("""
        update RefreshToken rt
           set rt.revokedAt = :now
         where rt.userId = :userId
           and rt.revokedAt is null
    """)
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    Optional<RefreshToken> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("""
        update RefreshToken rt
           set rt.revokedAt = :now
         where rt.id = :id
           and rt.userId = :userId
           and rt.revokedAt is null
    """)
    int revokeOne(@Param("id") Long id,
                  @Param("userId") Long userId,
                  @Param("now") Instant now);

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(Long userId);
}