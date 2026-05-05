package uk.co.quietadmin.domain.signup;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "pending_signup", indexes = {
        @Index(name = "ix_pending_signup_token", columnList = "token", unique = true),
        @Index(name = "ix_pending_signup_expires", columnList = "expires_at"),
        @Index(name = "idx_pending_signup_status", columnList = "status"),
        @Index(name = "ux_pending_signup_checkout_session", columnList = "stripe_checkout_session_id", unique = true)
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class PendingSignup {

    public enum PendingSignupStatus {
        PENDING_CHECKOUT,
        CHECKOUT_COMPLETED,
        ACCOUNT_CREATED,
        EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Stable public token (used as metadata key in Stripe Checkout).
     */
    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PendingSignupStatus status;

    @Column(nullable = false)
    private String email;

    @Column(name="first_name", nullable = false)
    private String firstName;

    @Column(name="last_name", nullable = false)
    private String lastName;

    @Column(name="group_name", nullable = false)
    private String groupName;

    @Column(name="password_hash")
    private String passwordHash;

    // Email verification for the user that will be created post-checkout
    @Column(name="email_verification_token", length = 128)
    private String emailVerificationToken;

    @Column(name="email_verification_expires_at")
    private Instant emailVerificationExpiresAt;

    // Stripe linkage
    @Column(name = "stripe_checkout_session_id", length = 128)
    private String stripeCheckoutSessionId;

    @Column(name = "stripe_customer_id", length = 64)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 64)
    private String stripeSubscriptionId;

    @Column(name="created_at", nullable = false)
    private Instant createdAt;

    @Column(name="expires_at", nullable = false)
    private Instant expiresAt;
}
