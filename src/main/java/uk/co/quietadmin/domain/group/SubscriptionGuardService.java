package uk.co.quietadmin.domain.group;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SubscriptionGuardService {

    public void assertSubscriptionActive(QaGroup group) {

        SubscriptionStatus status = group.getSubscriptionStatus();

        if (status == SubscriptionStatus.CANCELLED ||
                status == SubscriptionStatus.PAST_DUE) {
            throw new IllegalArgumentException("Subscription inactive");
        }

        if (status == SubscriptionStatus.TRIAL &&
                group.getTrialEndsAt() != null &&
                group.getTrialEndsAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Trial expired");
        }
    }
}