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
import uk.co.quietadmin.domain.team.NoticeTeamVisibilityRepository;
import uk.co.quietadmin.domain.team.TeamRepository;
import uk.co.quietadmin.helpers.TestFactory;
import uk.co.quietadmin.security.SecurityEventService;
import uk.co.quietadmin.service.notice.NoticeService;

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

        Notice result = noticeService.create(membership(), input);

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
}
