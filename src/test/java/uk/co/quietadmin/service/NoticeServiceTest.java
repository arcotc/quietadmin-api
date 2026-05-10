package uk.co.quietadmin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.notice.Notice;
import uk.co.quietadmin.domain.notice.NoticeRepository;
import uk.co.quietadmin.domain.notice.NoticeStatus;
import uk.co.quietadmin.domain.notice.NoticeTeamVisibility;
import uk.co.quietadmin.domain.notice.NoticeTeamVisibilityRepository;
import uk.co.quietadmin.domain.team.Team;
import uk.co.quietadmin.domain.team.TeamRepository;
import uk.co.quietadmin.helpers.TestFactory;
import uk.co.quietadmin.security.SecurityEventService;
import uk.co.quietadmin.service.notice.NoticeService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock NoticeRepository noticeRepository;
    @Mock NoticeTeamVisibilityRepository noticeTeamVisibilityRepository;
    @Mock TeamRepository teamRepository;
    @Mock SecurityEventService securityEventService;

    @InjectMocks NoticeService noticeService;

    private static final long GROUP_ID = 1L;
    private static final long USER_ID = 10L;

    private Membership membership() {
        return TestFactory.adminMembership(USER_ID, GROUP_ID);
    }

    @Test
    void create_defaultsToDraftWhenStatusIsNull() {
        Notice input = new Notice();
        input.setGroupId(GROUP_ID);
        input.setTitle("Test");
        input.setContent("Content");
        input.setStatus(null);
        input.setCreatedBy(USER_ID);

        Notice saved = TestFactory.draftNotice(1L, GROUP_ID, USER_ID);
        when(noticeRepository.save(any(Notice.class))).thenReturn(saved);
        when(noticeRepository.findByIdAndGroupId(1L, GROUP_ID)).thenReturn(Optional.of(saved));
        when(noticeTeamVisibilityRepository.findByNoticeIdIn(List.of(1L))).thenReturn(List.of());

        Notice result = noticeService.create(membership(), input, List.of());

        assertThat(input.getStatus()).isEqualTo(NoticeStatus.DRAFT);
        assertThat(result.getStatus()).isEqualTo(NoticeStatus.DRAFT);
    }

    @Test
    void publish_draftBecomesActive() {
        Notice draft = TestFactory.draftNotice(1L, GROUP_ID, USER_ID);
        when(noticeRepository.findByIdAndGroupId(1L, GROUP_ID))
                .thenReturn(Optional.of(draft));
        when(noticeRepository.save(any(Notice.class))).thenAnswer(inv -> inv.getArgument(0));

        Notice result = noticeService.publish(1L, membership());

        assertThat(result.getStatus()).isEqualTo(NoticeStatus.ACTIVE);
    }

    @Test
    void publish_expiredNoticeThrows() {
        Notice expired = TestFactory.expiredNotice(2L, GROUP_ID, USER_ID);
        when(noticeRepository.findByIdAndGroupId(2L, GROUP_ID))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> noticeService.publish(2L, membership()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void delete_removesFromCorrectGroup() {
        Notice notice = TestFactory.activeNotice(3L, GROUP_ID, USER_ID);
        when(noticeRepository.findByIdAndGroupId(3L, GROUP_ID))
                .thenReturn(Optional.of(notice));

        noticeService.delete(membership(), 3L);

        verify(noticeRepository).delete(notice);
    }

    @Test
    void delete_crossGroupAccessDenied() {
        when(noticeRepository.findByIdAndGroupId(3L, GROUP_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.delete(membership(), 3L))
                .isInstanceOf(AccessDeniedException.class);

        verify(noticeRepository, never()).delete(any());
    }

    @Test
    void update_savesOnlyTeamsFromTheNoticeGroup() {
        Notice notice = TestFactory.draftNotice(9L, GROUP_ID, USER_ID);
        Team team = TestFactory.team(20L, GROUP_ID, "Stewards");

        when(noticeRepository.findByIdAndGroupId(9L, GROUP_ID))
                .thenReturn(Optional.of(notice));
        when(noticeRepository.save(any(Notice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(teamRepository.findByIdAndGroupIdAndDeletedAtIsNull(20L, GROUP_ID))
                .thenReturn(Optional.of(team));
        when(noticeTeamVisibilityRepository.findByNoticeIdIn(List.of(9L))).thenReturn(List.of());

        noticeService.update(9L, GROUP_ID, "Title", "Content", null, List.of(20L, 20L));

        verify(noticeTeamVisibilityRepository).deleteByNoticeId(9L);
        verify(noticeTeamVisibilityRepository, times(1)).save(any(NoticeTeamVisibility.class));
    }

    @Test
    void update_crossGroupTeamThrows() {
        Notice notice = TestFactory.draftNotice(9L, GROUP_ID, USER_ID);

        when(noticeRepository.findByIdAndGroupId(9L, GROUP_ID))
                .thenReturn(Optional.of(notice));
        when(noticeRepository.save(any(Notice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(teamRepository.findByIdAndGroupIdAndDeletedAtIsNull(99L, GROUP_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.update(9L, GROUP_ID, "Title", "Content", null, List.of(99L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Forbidden team");
    }

    @Test
    void getActiveNoticeById_nonAdminCannotFetchNonVisibleNotice() {
        Membership member = TestFactory.memberMembership(20L, GROUP_ID);

        when(noticeRepository.findVisibleActiveNoticeById(
                eq(9L),
                eq(GROUP_ID),
                eq(20L),
                any()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.getActiveNoticeById(9L, member))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Notice not found");

        verify(noticeRepository, never()).findByIdAndGroupId(anyLong(), anyLong());
    }
}
