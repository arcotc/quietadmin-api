package uk.co.quietadmin.domain.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    Optional<Invitation> findTopByEmailAndAcceptedAtIsNull(String email);
}
