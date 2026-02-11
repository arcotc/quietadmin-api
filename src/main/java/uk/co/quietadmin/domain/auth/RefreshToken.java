package uk.co.quietadmin.domain.auth;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Data
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable = false)
    private Long userId;

    @Column(name="token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name="created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name="expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name="revoked_at")
    private Instant revokedAt;

    @Column(name="replaced_by_token_hash", length = 255)
    private String replacedByTokenHash;

    @Column(name="last_used_at")
    private Instant lastUsedAt;

    @Column(name="user_agent", length = 255)
    private String userAgent;

    @Column(name="ip_address", length = 64)
    private String ipAddress;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}