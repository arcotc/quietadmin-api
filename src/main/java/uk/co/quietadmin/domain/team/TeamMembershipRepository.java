package uk.co.quietadmin.domain.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, Long> {

    List<TeamMembership> findByTeamId(Long teamId);

    List<TeamMembership> findByUserId(Long userId);

    void deleteByTeamIdAndUserId(Long teamId, Long userId);

    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    long countByTeamId(Long teamId);

    @Query("""
            select count(distinct tm.userId)
            from TeamMembership tm
            join Membership m on m.userId = tm.userId
            where tm.teamId = :teamId
              and m.groupId = :groupId
            """)
    long countVisibleMembersByTeamIdAndGroupId(
            @Param("teamId") Long teamId,
            @Param("groupId") Long groupId
    );
}
