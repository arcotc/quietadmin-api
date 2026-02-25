package uk.co.quietadmin.domain.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "password_reset_token")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable = false)
    private Long userId;

    @Column(name="token_hash", nullable = false)
    private String tokenHash;

    @Column(name="expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name="used_at")
    private Instant usedAt;

    @Column(name="created_at", nullable = false)
    private Instant createdAt;

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}