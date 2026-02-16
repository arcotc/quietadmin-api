package uk.co.quietadmin.domain.group;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SubscriptionGuardService {

    public void assertSubscriptionActive(QaGroup group) {

        // ---------------------------------------------------------
        // NEW: Stripe-authoritative path (preferred)
        // ---------------------------------------------------------
        if (group.getStripeSubscriptionId() != null && !group.getStripeSubscriptionId().isBlank()) {

            StripeSubscriptionStatus s = group.getStripeSubscriptionStatus();
            if (s == null) {
                throw new IllegalArgumentException("Subscription status unknown");
            }

            // Allow during trial and active
            if (s == StripeSubscriptionStatus.TRIALING || s == StripeSubscriptionStatus.ACTIVE) {
                return;
            }

            // Past due: you can decide grace policy.
            // Minimal strict policy: block immediately.
            if (s == StripeSubscriptionStatus.PAST_DUE ||
                    s == StripeSubscriptionStatus.PAST_DUE ||
                    s == StripeSubscriptionStatus.INCOMPLETE ||
                    s == StripeSubscriptionStatus.INCOMPLETE_EXPIRED) {
                throw new IllegalArgumentException("Payment required");
            }

            // Canceled / ended
            if (s == StripeSubscriptionStatus.CANCELED || s == StripeSubscriptionStatus.UNPAID) {
                throw new IllegalArgumentException("Subscription cancelled");
            }

            // Default deny
            throw new IllegalArgumentException("Subscription inactive");
        }

        // ---------------------------------------------------------
        // Legacy fallback (migration window)
        // ---------------------------------------------------------
        StripeSubscriptionStatus status = group.getStripeSubscriptionStatus();

        if (status == StripeSubscriptionStatus.CANCELED || status == StripeSubscriptionStatus.PAST_DUE) {
            throw new IllegalArgumentException("Subscription inactive");
        }

        if (status == StripeSubscriptionStatus.TRIALING &&
                group.getStripeTrialEnd() != null &&
                group.getStripeTrialEnd().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Trial expired");
        }
    }
}
