package uk.co.quietadmin.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
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
@Slf4j
public class NoticeController {

    private final NoticeService noticeService;
    private final CurrentUserService currentUserService;

    @GetMapping("/active")
    public ResponseEntity<List<Notice>> active(Principal principal) {
        Membership membership = currentUserService.getMembership(principal.getName());
        return ResponseEntity.ok(
                noticeService.getActiveNotices(
                        membership.getGroupId(),
                        membership.getUserId()
                )
        );
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

        String clean = Jsoup.clean(request.content(), Safelist.basic());

        Notice notice = new Notice();
        notice.setGroupId(membership.getGroupId());
        notice.setTitle(request.title());
        notice.setContent(clean);
        notice.setExpiresAt(request.expiresAt());
        notice.setCreatedBy(membership.getUserId());

        return ResponseEntity.ok(noticeService.create(membership, notice, request.teamIds()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Notice> update(
            Principal principal,
            @PathVariable Long id,
            @RequestBody UpdateNoticeRequest request
    ) {
        currentUserService.requireAdmin(principal.getName());

        Membership membership = currentUserService.getMembership(principal.getName());

        String clean = Jsoup.clean(request.content(), Safelist.basic());

        return ResponseEntity.ok(
                noticeService.update(
                        id,
                        membership.getGroupId(),
                        request.title(),
                        clean,
                        request.expiresAt(),
                        request.teamIds()
                )
        );
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Notice> publish(Principal principal, @PathVariable Long id) {
        currentUserService.requireAdmin(principal.getName());
        Membership membership = currentUserService.getMembership(principal.getName());
        return ResponseEntity.ok(noticeService.publish(id, membership));
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<Notice> unpublish(Principal principal, @PathVariable Long id) {
        currentUserService.requireAdmin(principal.getName());
        Membership membership = currentUserService.getMembership(principal.getName());
        return ResponseEntity.ok(noticeService.unpublish(membership, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long id) {
        currentUserService.requireAdmin(principal.getName());

        Membership membership = currentUserService.getMembership(principal.getName());
        noticeService.delete(membership, id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<Notice>> all(Principal principal) {
        Membership membership = currentUserService.getMembership(principal.getName());
        if (membership.isAdmin()) {
            return ResponseEntity.ok(noticeService.getAllForGroup(membership.getGroupId()));
        } else {
            return ResponseEntity.ok(noticeService.getActiveNotices(membership.getGroupId(), membership.getUserId()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Notice> getOne(
            Principal principal,
            @PathVariable Long id
    ) {
        Membership membership = currentUserService.getMembership(principal.getName());

        Notice notice = noticeService.getActiveNoticeById(id, membership);

        return ResponseEntity.ok(notice);
    }

    public record CreateNoticeRequest(
            String title,
            String content,
            Instant expiresAt,
            List<Long> teamIds
    ) {}

    public record UpdateNoticeRequest(
            String title,
            String content,
            Instant expiresAt,
            List<Long> teamIds
    ) {}
}
