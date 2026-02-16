package uk.co.quietadmin.domain.group;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Data
@Table(name = "qa_group", indexes = {
        @Index(name = "idx_qagroup_stripe_customer", columnList = "stripe_customer_id"),
        @Index(name = "idx_qagroup_stripe_subscription", columnList = "stripe_subscription_id"),
        @Index(name = "idx_qagroup_stripe_status", columnList = "stripe_subscription_status")
})
public class QaGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false)
    private PlanType planType;

    // =========================================================
    // Stripe authoritative linkage + lifecycle snapshot fields
    // =========================================================

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stripe_subscription_status")
    private StripeSubscriptionStatus stripeSubscriptionStatus; // e.g. trialing, active, past_due, canceled

    @Column(name = "stripe_trial_end")
    private Instant stripeTrialEnd;

    @Column(name = "stripe_current_period_end")
    private Instant stripeCurrentPeriodEnd;

    @Column(name = "stripe_cancel_at")
    private Instant stripeCancelAt;

    @Column(name = "stripe_canceled_at")
    private Instant stripeCanceledAt;

    @Column(name = "stripe_last_event_at")
    private Instant stripeLastEventAt;

    @Column(name = "stripe_last_sync_at")
    private Instant stripeLastSyncAt;

    // =========================================================

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expiry_reminder_hours")
    private Integer expiryReminderHours;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
