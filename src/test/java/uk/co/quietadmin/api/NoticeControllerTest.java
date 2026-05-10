package uk.co.quietadmin.api;

import org.junit.jupiter.api.Test;
import uk.co.quietadmin.domain.customer.CurrentUserService;
import uk.co.quietadmin.service.notice.NoticeService;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NoticeControllerTest {

    @Test
    void update_requiresAdmin() {
        NoticeService noticeService = mock(NoticeService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        NoticeController controller = new NoticeController(noticeService, currentUserService);
        Principal principal = () -> "member@test.com";

        doThrow(new org.springframework.security.access.AccessDeniedException("Admin access required."))
                .when(currentUserService)
                .requireAdmin("member@test.com");

        assertThatThrownBy(() -> controller.update(
                principal,
                1L,
                new NoticeController.UpdateNoticeRequest(
                        "Title",
                        "Content",
                        Instant.now(),
                        List.of(10L)
                )
        )).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verifyNoInteractions(noticeService);
    }
}
