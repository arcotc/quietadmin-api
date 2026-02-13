package uk.co.quietadmin.domain.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QaGroupRepository extends JpaRepository<QaGroup, Long> {

    List<QaGroup> findByExpiryReminderHoursIsNotNull();

}