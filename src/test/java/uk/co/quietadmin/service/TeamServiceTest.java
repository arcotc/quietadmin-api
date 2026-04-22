package uk.co.quietadmin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import uk.co.quietadmin.domain.customer.CurrentUserService;
import uk.co.quietadmin.domain.team.Team;
import uk.co.quietadmin.domain.team.TeamMembershipRepository;
import uk.co.quietadmin.domain.team.TeamRepository;
import uk.co.quietadmin.domain.team.dto.TeamDto;
import uk.co.quietadmin.domain.user.UserAccountRepository;
import uk.co.quietadmin.domain.group.MembershipRepository;
import uk.co.quietadmin.helpers.TestFactory;
import uk.co.quietadmin.security.SecurityEventService;
import uk.co.quietadmin.service.team.TeamService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamServiceTest {

    @Mock TeamRepository teamRepository;
    @Mock TeamMembershipRepository teamMembershipRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock UserAccountRepository userAccountRepository;
    @Mock CurrentUserService currentUserService;
    @Mock SecurityEventService securityEventService;

    @InjectMocks TeamService teamService;

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String MEMBER_EMAIL = "member@test.com";
    private static final long GROUP_ID = 1L;
    private static final long ADMIN_ID = 10L;

    @BeforeEach
    void setUp() {
        when(currentUserService.getMembership(ADMIN_EMAIL))
                .thenReturn(TestFactory.adminMembership(ADMIN_ID, GROUP_ID));
        when(currentUserService.getMembership(MEMBER_EMAIL))
                .thenReturn(TestFactory.memberMembership(20L, GROUP_ID));
    }

    @Test
    void createTeam_adminSucceeds() {
        doNothing().when(currentUserService).requireAdmin(ADMIN_EMAIL);
        when(teamRepository.existsByGroupIdAndNameIgnoreCaseAndDeletedAtIsNull(GROUP_ID, "Stewards"))
                .thenReturn(false);

        Team saved = TestFactory.team(99L, GROUP_ID, "Stewards");
        when(teamRepository.save(any(Team.class))).thenReturn(saved);

        TeamDto result = teamService.createTeam(ADMIN_EMAIL, "Stewards", null);

        assertThat(result.name()).isEqualTo("Stewards");
        verify(teamRepository).save(any(Team.class));
    }

    @Test
    void createTeam_nonAdminIsBlocked() {
        doThrow(new AccessDeniedException("Admin access required."))
                .when(currentUserService).requireAdmin(MEMBER_EMAIL);

        assertThatThrownBy(() -> teamService.createTeam(MEMBER_EMAIL, "Stewards", null))
                .isInstanceOf(AccessDeniedException.class);

        verify(teamRepository, never()).save(any());
    }

    @Test
    void createTeam_blankNameThrows() {
        doNothing().when(currentUserService).requireAdmin(ADMIN_EMAIL);

        assertThatThrownBy(() -> teamService.createTeam(ADMIN_EMAIL, "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Team name is required");
    }

    @Test
    void createTeam_duplicateNameThrows() {
        doNothing().when(currentUserService).requireAdmin(ADMIN_EMAIL);
        when(teamRepository.existsByGroupIdAndNameIgnoreCaseAndDeletedAtIsNull(GROUP_ID, "Stewards"))
                .thenReturn(true);

        assertThatThrownBy(() -> teamService.createTeam(ADMIN_EMAIL, "Stewards", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Team already exists");
    }

    @Test
    void deleteTeam_adminSoftDeletes() {
        doNothing().when(currentUserService).requireAdmin(ADMIN_EMAIL);
        Team team = TestFactory.team(5L, GROUP_ID, "Stewards");
        when(teamRepository.findByIdAndGroupIdAndDeletedAtIsNull(5L, GROUP_ID))
                .thenReturn(Optional.of(team));
        when(teamRepository.save(any(Team.class))).thenReturn(team);

        teamService.deleteTeam(ADMIN_EMAIL, 5L);

        assertThat(team.getDeletedAt()).isNotNull();
        verify(teamRepository).save(team);
    }

    @Test
    void addMember_crossGroupUserIsBlocked() {
        doNothing().when(currentUserService).requireAdmin(ADMIN_EMAIL);
        long outsiderUserId = 999L;
        Team team = TestFactory.team(5L, GROUP_ID, "Stewards");
        when(teamRepository.findByIdAndGroupIdAndDeletedAtIsNull(5L, GROUP_ID))
                .thenReturn(Optional.of(team));
        when(teamMembershipRepository.existsByTeamIdAndUserId(5L, outsiderUserId))
                .thenReturn(false);
        when(membershipRepository.existsByUserIdAndGroupId(outsiderUserId, GROUP_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> teamService.addMember(ADMIN_EMAIL, 5L, outsiderUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User not in group");
    }
}
