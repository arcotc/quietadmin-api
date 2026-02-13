package uk.co.quietadmin.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.domain.customer.CurrentUserService;
import uk.co.quietadmin.domain.group.Membership;
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
    public ResponseEntity<List<Notice>> active(Principal principal) {
        Membership membership = currentUserService.getMembership(principal.getName());
        return ResponseEntity.ok(noticeService.getActiveNotices(membership.getGroupId()));
    }

    @GetMapping("/drafts")
    public ResponseEntity<List<Notice>> drafts(Principal principal) {
        currentUserService.requireAdmin(principal.getName());
        Membership membership = currentUserService.getMembership(principal.getName());
        return ResponseEntity.ok(noticeService.getDrafts(membership.getGroupId()));
    }

    @PostMapping
    public ResponseEntity<Notice> create(
            Principal principal,
            @RequestBody CreateNoticeRequest request
    ) {
        currentUserService.requireAdmin(principal.getName());

        Membership membership = currentUserService.getMembership(principal.getName());

        Notice notice = new Notice();
        notice.setGroupId(membership.getGroupId());
        notice.setTitle(request.title());
        notice.setContent(request.content());
        notice.setExpiresAt(request.expiresAt());
        notice.setCreatedBy(membership.getUserId());

        return ResponseEntity.ok(noticeService.create(notice));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Notice> update(
            Principal principal,
            @PathVariable Long id,
            @RequestBody UpdateNoticeRequest request
    ) {
        currentUserService.requireAdmin(principal.getName());

        Membership membership = currentUserService.getMembership(principal.getName());

        return ResponseEntity.ok(
                noticeService.update(
                        id,
                        membership.getGroupId(),
                        request.title(),
                        request.content(),
                        request.expiresAt()
                )
        );
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Notice> publish(Principal principal, @PathVariable Long id) {
        currentUserService.requireAdmin(principal.getName());
        Membership membership = currentUserService.getMembership(principal.getName());
        return ResponseEntity.ok(noticeService.publish(id, membership.getGroupId()));
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<Notice> unpublish(Principal principal, @PathVariable Long id) {
        currentUserService.requireAdmin(principal.getName());
        Membership membership = currentUserService.getMembership(principal.getName());
        return ResponseEntity.ok(noticeService.unpublish(id, membership.getGroupId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long id) {
        currentUserService.requireAdmin(principal.getName());

        Membership membership = currentUserService.getMembership(principal.getName());
        noticeService.delete(id, membership.getGroupId());

        return ResponseEntity.noContent().build();
    }

    public record CreateNoticeRequest(
            String title,
            String content,
            Instant expiresAt
    ) {}

    public record UpdateNoticeRequest(
            String title,
            String content,
            Instant expiresAt
    ) {}
}