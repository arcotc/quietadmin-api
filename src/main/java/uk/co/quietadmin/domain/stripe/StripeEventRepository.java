package uk.co.quietadmin.domain.stripe;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeEventRepository extends JpaRepository<StripeEvent, Long> {
    boolean existsByEventId(String eventId);
}