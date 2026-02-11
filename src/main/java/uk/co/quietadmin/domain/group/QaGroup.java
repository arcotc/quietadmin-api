package uk.co.quietadmin.domain.group;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Data
@Table(name = "qa_group")
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

    @Column(name = "subscription_status", nullable = false)
    private String subscriptionStatus = "TRIAL";

    @Column(name = "plan_type", nullable = false)
    private String planType = "STANDARD";

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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