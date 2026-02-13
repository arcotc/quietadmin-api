package uk.co.quietadmin.domain.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
    Optional<Membership> findByUserId(Long id);

    List<Membership> findByGroupIdAndRole(Long groupId, String role);
}