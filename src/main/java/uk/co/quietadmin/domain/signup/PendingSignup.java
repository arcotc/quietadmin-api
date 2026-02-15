package uk.co.quietadmin.domain.signup;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "pending_signup", indexes = {
        @Index(name = "ix_pending_signup_token", columnList = "token", unique = true),
        @Index(name = "ix_pending_signup_expires", columnList = "expires_at")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class PendingSignup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @Column(nullable = false)
    private String email;

    @Column(name="first_name", nullable = false)
    private String firstName;

    @Column(name="group_name", nullable = false)
    private String groupName;

    @Column(name="password_hash", nullable = false)
    private String passwordHash;

    @Column(name="created_at", nullable = false)
    private Instant createdAt;

    @Column(name="expires_at", nullable = false)
    private Instant expiresAt;
}