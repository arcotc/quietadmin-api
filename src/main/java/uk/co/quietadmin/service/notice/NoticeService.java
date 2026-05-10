package uk.co.quietadmin.service.notice;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.notice.Notice;
import uk.co.quietadmin.domain.notice.NoticeRepository;

import org.springframework.security.access.AccessDeniedException;
import uk.co.quietadmin.domain.notice.NoticeStatus;
import uk.co.quietadmin.domain.notice.NoticeTeamVisibility;
import uk.co.quietadmin.domain.notice.NoticeTeamVisibilityRepository;
import uk.co.quietadmin.domain.team.TeamRepository;
import uk.co.quietadmin.security.SecurityEventService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class NoticeService {
    private final NoticeRepository noticeRepository;
    private final NoticeTeamVisibilityRepository noticeTeamVisibilityRepository;
    private final TeamRepository teamRepository;
    private final SecurityEventService securityEventService;

    @Transactional(readOnly = true)
    public List<Notice> getActiveNotices(Long groupId, Long userId) {
        return noticeRepository.findVisibleActiveNotices(
                groupId,
                userId,
                Instant.now()
        );
    }

    public Notice create(Membership membership, Notice notice, List<Long> teamIds) {
        if (notice.getStatus() == null) {
            notice.setStatus(NoticeStatus.DRAFT);
        }

        Notice saved = noticeRepository.save(notice);
        replaceVisibility(saved.getId(), membership.getGroupId(), teamIds);
        attachVisibility(List.of(saved));

        securityEventService.audit(
                "NOTICE_CREATED",
                membership.getUserId(),
                membership.getGroupId(),
                Map.of("noticeId", saved.getId())
        );

        return saved;
    }

    public Notice update(Long id, Long groupId, String title, String content, Instant expiresAt, List<Long> teamIds) throws AccessDeniedException {
        Notice n = noticeRepository.findByIdAndGroupId(id, groupId)
                .orElseThrow(() -> new AccessDeniedException("Not your group"));

        n.setTitle(title);
        n.setContent(content);
        n.setExpiresAt(expiresAt);

        Notice saved = noticeRepository.save(n);
        replaceVisibility(saved.getId(), groupId, teamIds);
        attachVisibility(List.of(saved));
        return saved;
    }

    public void delete(Membership membership, Long id) throws AccessDeniedException {
        Notice n = noticeRepository.findByIdAndGroupId(id, membership.getGroupId())
                .orElseThrow(() -> new AccessDeniedException("Not your group"));

        securityEventService.audit(
                "NOTICE_DELETED",
                membership.getUserId(),
                membership.getGroupId(),
                Map.of("noticeId", n.getId())
        );

        noticeRepository.delete(n);
    }

    public Notice publish(Long id, Membership membership) {
        Notice n = noticeRepository.findByIdAndGroupId(id, membership.getGroupId())
                .orElseThrow(() -> new AccessDeniedException("Not your group"));

        if (n.getStatus() == NoticeStatus.EXPIRED) {
            throw new IllegalStateException("Cannot publish an expired notice.");
        }

        securityEventService.audit(
                "NOTICE_PUBLISHED",
                membership.getUserId(),
                membership.getGroupId(),
                Map.of("noticeId", n.getId())
        );

        n.setStatus(NoticeStatus.ACTIVE);
        return noticeRepository.save(n);
    }

    public Notice unpublish(Membership membership, Long id) {
        Notice n = noticeRepository.findByIdAndGroupId(id, membership.getGroupId())
                .orElseThrow(() -> new AccessDeniedException("Not your group"));

        if (n.getStatus() == NoticeStatus.EXPIRED) {
            throw new IllegalStateException("Cannot unpublish an expired notice.");
        }

        n.setStatus(NoticeStatus.DRAFT);

        securityEventService.audit(
                "NOTICE_UNPUBLISHED",
                membership.getUserId(),
                membership.getGroupId(),
                Map.of("noticeId", n.getId())
        );

        return noticeRepository.save(n);
    }

    @Transactional(readOnly = true)
    public List<Notice> getDrafts(Long groupId) {
        return attachVisibility(noticeRepository.findDraftNotices(groupId));
    }

    @Transactional(readOnly = true)
    public List<Notice> getAllForGroup(Long groupId) {
        return attachVisibility(noticeRepository.findByGroupIdOrdered(groupId));
    }

    @Transactional(readOnly = true)
    public Notice getActiveNoticeById(Long id, Membership membership) {

        Notice notice;

        if (membership.isAdmin()) {
            notice = noticeRepository.findActiveNoticeByIdAndGroupId(
                            id,
                            membership.getGroupId(),
                            Instant.now()
                    )
                    .orElseThrow(() -> new RuntimeException("Notice not found"));
        } else {
            notice = noticeRepository.findVisibleActiveNoticeById(
                        id,
                        membership.getGroupId(),
                        membership.getUserId(),
                        Instant.now()
                ).orElseThrow(() -> new RuntimeException("Notice not found"));
        }

        if (membership.isAdmin()) {
            attachVisibility(List.of(notice));
        }
        return notice;
    }

    private void replaceVisibility(Long noticeId, Long groupId, List<Long> teamIds) {

        noticeRepository.findByIdAndGroupId(noticeId, groupId)
                .orElseThrow(() -> new RuntimeException("Not your group"));

        noticeTeamVisibilityRepository.deleteByNoticeId(noticeId);

        if (teamIds == null || teamIds.isEmpty()) return;

        for (Long teamId : teamIds.stream().distinct().toList()) {

            teamRepository.findByIdAndGroupIdAndDeletedAtIsNull(teamId, groupId)
                    .orElseThrow(() -> new RuntimeException("Forbidden team."));

            NoticeTeamVisibility ntv = new NoticeTeamVisibility();
            ntv.setNoticeId(noticeId);
            ntv.setTeamId(teamId);

            noticeTeamVisibilityRepository.save(ntv);
        }
    }

    private List<Notice> attachVisibility(List<Notice> notices) {
        if (notices == null || notices.isEmpty()) {
            return notices;
        }

        List<Long> noticeIds = notices.stream()
                .map(Notice::getId)
                .toList();

        Map<Long, List<Long>> teamIdsByNoticeId = noticeTeamVisibilityRepository
                .findByNoticeIdIn(noticeIds)
                .stream()
                .collect(Collectors.groupingBy(
                        NoticeTeamVisibility::getNoticeId,
                        Collectors.mapping(
                                NoticeTeamVisibility::getTeamId,
                                Collectors.toCollection(ArrayList::new)
                        )
                ));

        notices.forEach(notice -> notice.setTeamIds(
                teamIdsByNoticeId.getOrDefault(notice.getId(), List.of())
        ));

        return notices;
    }
}
