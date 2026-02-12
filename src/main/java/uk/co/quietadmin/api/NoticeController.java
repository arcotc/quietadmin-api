package uk.co.quietadmin.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.domain.customer.CurrentUserService;
import uk.co.quietadmin.domain.notice.Notice;
import uk.co.quietadmin.service.notice.NoticeService;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;
    private final CurrentUserService currentUserService;

    @GetMapping("/active")
    public List<Notice> active(Principal principal) {
        Long groupId = currentUserService.getCurrentGroupId(principal.getName());
        return noticeService.getActiveNotices(groupId);
    }
}