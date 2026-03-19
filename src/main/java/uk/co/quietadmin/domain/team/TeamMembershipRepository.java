package uk.co.quietadmin.domain.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, Long> {

    List<TeamMembership> findByTeamId(Long teamId);

    List<TeamMembership> findByUserId(Long userId);

    void deleteByTeamIdAndUserId(Long teamId, Long userId);

    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    long countByTeamId(Long teamId);
}