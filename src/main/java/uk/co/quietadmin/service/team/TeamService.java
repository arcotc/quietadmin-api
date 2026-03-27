package uk.co.quietadmin.service.team;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.customer.CurrentUserService;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.group.MembershipRepository;
import uk.co.quietadmin.domain.group.dto.MemberDto;
import uk.co.quietadmin.domain.team.Team;
import uk.co.quietadmin.domain.team.TeamMembership;
import uk.co.quietadmin.domain.team.TeamMembershipRepository;
import uk.co.quietadmin.domain.team.TeamRepository;
import uk.co.quietadmin.domain.team.dto.TeamDto;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;
import uk.co.quietadmin.security.SecurityEventService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {
    private final SecurityEventService securityEventService;
    private final TeamRepository teamRepository;
    private final CurrentUserService currentUserService;
    private final MembershipRepository membershipRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final UserAccountRepository userAccountRepository;

    /* ======================================================
       LIST TEAMS
       ====================================================== */

    @Transactional
    public List<TeamDto> listTeams(String username) {
        Membership membership = currentUserService.getMembership(username);

        List<Team> teams = teamRepository
                .findByGroupIdAndDeletedAtIsNullOrderByNameAsc(
                        membership.getGroupId()
                );

        // ✅ Get all teams the current user belongs to (ONE query)
        Set<Long> myTeamIds = teamMembershipRepository
                .findByUserId(membership.getUserId())
                .stream()
                .map(TeamMembership::getTeamId)
                .collect(Collectors.toSet());

        return teams.stream()
                .map(team -> {
                    long count = teamMembershipRepository.countByTeamId(team.getId());

                    boolean isMember = myTeamIds.contains(team.getId());

                    return new TeamDto(
                            team.getId(),
                            team.getName(),
                            team.getDescription(),
                            count,
                            isMember
                    );
                })

                // ✅ THIS IS WHERE YOUR SORT GOES
                .sorted((a, b) ->
                        Boolean.compare(
                                b.isMember(),   // true first
                                a.isMember()
                        )
                )

                .toList();
    }

    /* ======================================================
       CREATE TEAM
       ====================================================== */

    @Transactional
    public TeamDto createTeam(String username, String name, String description) {
        currentUserService.requireAdmin(username);

        Membership membership = currentUserService.getMembership(username);

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Team name is required.");
        }

        String cleanName = name.trim();

        if (teamRepository.existsByGroupIdAndNameIgnoreCaseAndDeletedAtIsNull(
                membership.getGroupId(),
                cleanName
        )) {
            throw new IllegalStateException("Team already exists.");
        }

        Team team = new Team();
        team.setGroupId(membership.getGroupId());
        team.setName(cleanName);
        team.setDescription(description != null ? description.trim() : null);

        Team saved = teamRepository.save(team);

        securityEventService.audit(
                "TEAM_CREATED",
                membership.getUserId(),
                membership.getGroupId(),
                Map.of(
                        "teamId", saved.getId(),
                        "teamName", saved.getName()
                )
        );

        return new TeamDto(
                saved.getId(),
                saved.getName(),
                saved.getDescription(),
                0L,          // no members yet
                false        // creator not auto-added
        );
    }

    @Transactional
    public TeamDto updateTeam(String username, Long teamId, String name, String description) {

        currentUserService.requireAdmin(username);

        Membership membership = currentUserService.getMembership(username);

        Team team = teamRepository
                .findByIdAndGroupIdAndDeletedAtIsNull(teamId, membership.getGroupId())
                .orElseThrow(() -> new IllegalStateException("Team not found"));

        team.setName(name != null ? name.trim() : null);
        team.setDescription(description != null ? description.trim() : null);

        teamRepository.save(team);

        long membersCount =
                teamMembershipRepository.countByTeamId(team.getId());

        boolean isMember =
                teamMembershipRepository.existsByTeamIdAndUserId(
                        team.getId(),
                        membership.getUserId()
                );

        return new TeamDto(
                team.getId(),
                team.getName(),
                team.getDescription(),
                membersCount,
                isMember
        );
    }



    /* ======================================================
       DELETE TEAM (SOFT DELETE)
       ====================================================== */

    @Transactional
    public void deleteTeam(String username, Long teamId) {

        currentUserService.requireAdmin(username);

        Membership membership = currentUserService.getMembership(username);

        Team team = teamRepository
                .findByIdAndGroupIdAndDeletedAtIsNull(
                        teamId,
                        membership.getGroupId()
                )
                .orElseThrow(() ->
                        new IllegalStateException("Team not found")
                );

        team.setDeletedAt(Instant.now());

        securityEventService.audit(
                "TEAM_DELETED",
                membership.getUserId(),
                membership.getGroupId(),
                Map.of("teamId", team.getId())
        );

        teamRepository.save(team);
    }

    @Transactional
    public void addMember(String username, Long teamId, Long userId) {

        currentUserService.requireAdmin(username);

        Membership membership = currentUserService.getMembership(username);

        Team team = teamRepository
                .findByIdAndGroupIdAndDeletedAtIsNull(teamId, membership.getGroupId())
                .orElseThrow(() -> new IllegalStateException("Team not found"));

        // prevent duplicates
        if (teamMembershipRepository.existsByTeamIdAndUserId(teamId, userId)) {
            return; // idempotent
        }

        if (!membershipRepository.existsByUserIdAndGroupId(userId, membership.getGroupId())) {
            throw new IllegalStateException("User not in group");
        }

        TeamMembership tm = new TeamMembership();
        tm.setTeamId(teamId);
        tm.setUserId(userId);

        securityEventService.audit(
                "TEAM_ADD_MEMBER",
                membership.getUserId(),
                membership.getGroupId(),
                Map.of("teamId", team.getId())
        );

        teamMembershipRepository.save(tm);
    }

    @Transactional
    public void removeMember(String username, Long teamId, Long userId) {
        currentUserService.requireAdmin(username);

        Membership membership = currentUserService.getMembership(username);

        if (!membershipRepository.existsByUserIdAndGroupId(userId, membership.getGroupId())) {
            throw new IllegalStateException("User not in group");
        }

        teamRepository
                .findByIdAndGroupIdAndDeletedAtIsNull(teamId, membership.getGroupId())
                .orElseThrow(() -> new IllegalStateException("Team not found"));

        securityEventService.audit(
                "TEAM_REMOVE_MEMBER",
                membership.getUserId(),
                membership.getGroupId(),
                Map.of("teamId", teamId)
        );

        teamMembershipRepository.deleteByTeamIdAndUserId(teamId, userId);
    }

    @Transactional
    public List<MemberDto> getMembersForView(String username, Long teamId) {

        Membership membership = currentUserService.getMembership(username);

        // ✅ SECURITY: ensure team belongs to user's group
        Team team = teamRepository
                .findByIdAndGroupIdAndDeletedAtIsNull(teamId, membership.getGroupId())
                .orElseThrow(() -> new IllegalStateException("Team not found"));

        // ✅ get team memberships
        List<TeamMembership> memberships =
                teamMembershipRepository.findByTeamId(teamId);

        List<Long> userIds = memberships.stream()
                .map(TeamMembership::getUserId)
                .toList();

        if (userIds.isEmpty()) {
            return List.of();
        }

        // ✅ fetch users
        List<UserAccount> users =
                userAccountRepository.findByIdIn(userIds);

        // ✅ fetch group memberships (for role/status)
        List<Membership> groupMemberships =
                membershipRepository.findByUserIdInAndGroupId(
                        userIds,
                        membership.getGroupId()
                );

        Map<Long, Membership> membershipMap =
                groupMemberships.stream()
                        .collect(Collectors.toMap(Membership::getUserId, m -> m));

        return users.stream()
                .map(u -> {
                    Membership m = membershipMap.get(u.getId());

                    if (m == null) {
                        throw new IllegalStateException("User not in group");
                    }

                    return new MemberDto(
                            u.getId(),
                            u.getFirstName(),
                            u.getLastName(),
                            u.getEmail(),
                            m.getRole(),
                            u.getUserStatus()
                    );
                })
                .toList();
    }

    /* ======================================================
       MAPPER
       ====================================================== */

    private TeamDto toDto(Team team, Set<Long> myTeamIds) {
        long membersCount =
                teamMembershipRepository.countByTeamId(team.getId());

        boolean isMember = myTeamIds.contains(team.getId());

        return new TeamDto(
                team.getId(),
                team.getName(),
                team.getDescription(),
                membersCount,
                isMember
        );
    }

    @Transactional
    public TeamDto getTeam(String username, Long teamId) {

        Membership membership = currentUserService.getMembership(username);

        Team team = teamRepository
                .findByIdAndGroupIdAndDeletedAtIsNull(teamId, membership.getGroupId())
                .orElseThrow(() -> new IllegalStateException("Team not found"));

        long memberCount = teamMembershipRepository.countByTeamId(teamId);

        boolean isMember = teamMembershipRepository
                .existsByTeamIdAndUserId(teamId, membership.getUserId());

        return new TeamDto(
                team.getId(),
                team.getName(),
                team.getDescription(),
                memberCount,
                isMember
        );
    }
}