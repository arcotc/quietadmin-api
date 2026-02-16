package uk.co.quietadmin.domain.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QaGroupRepository extends JpaRepository<QaGroup, Long> {

    /**
     * Used by expiry reminder scheduler
     */
    List<QaGroup> findByExpiryReminderHoursIsNotNull();

    /**
     * Used by StripeWebhookService to map subscription lifecycle
     * events back to the correct group.
     */
    Optional<QaGroup> findByStripeSubscriptionId(String stripeSubscriptionId);

    /**
     * Optional helper (useful later for reconciliation or admin tools)
     */
    Optional<QaGroup> findByStripeCustomerId(String stripeCustomerId);

}
