package uk.co.quietadmin.service.notice;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.notice.Notice;
import uk.co.quietadmin.domain.notice.NoticeRepository;

import org.springframework.security.access.AccessDeniedException;
import uk.co.quietadmin.domain.notice.NoticeStatus;
import uk.co.quietadmin.domain.team.NoticeTeamVisibility;
import uk.co.quietadmin.domain.team.NoticeTeamVisibilityRepository;
import uk.co.quietadmin.domain.team.Team;
import uk.co.quietadmin.domain.team.TeamRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeService {
    private final NoticeRepository noticeRepository;
    private final NoticeTeamVisibilityRepository noticeTeamVisibilityRepository;
    private final TeamRepository teamRepository;

    public List<Notice> getActiveNotices(Long groupId, Long userId) {
        return noticeRepository.findVisibleActiveNotices(
                groupId,
                userId,
                Instant.now()
        );
    }

    public Notice create(Notice notice) {
        if (notice.getStatus() == null) {
            notice.setStatus(NoticeStatus.DRAFT);
        }
        return noticeRepository.save(notice);
    }

    public Notice update(Long id, Long groupId, String title, String content, Instant expiresAt) throws AccessDeniedException {
        Notice n = noticeRepository.findByIdAndGroupId(id, groupId)
                .orElseThrow(() -> new AccessDeniedException("Not your group"));

        if (!n.getGroupId().equals(groupId)) {
            throw new AccessDeniedException("Not your group.");
        }

        n.setTitle(title);
        n.setContent(content);
        n.setExpiresAt(expiresAt);

        return noticeRepository.save(n);
    }

    public void delete(Long id, Long groupId) throws AccessDeniedException {
        Notice n = noticeRepository.findByIdAndGroupId(id, groupId)
                .orElseThrow(() -> new AccessDeniedException("Not your group"));

        if (!n.getGroupId().equals(groupId)) {
            throw new AccessDeniedException("Not your group.");
        }

        noticeRepository.delete(n);
    }

    public Notice publish(Long id, Long groupId) {
        Notice n = noticeRepository.findByIdAndGroupId(id, groupId)
                .orElseThrow(() -> new AccessDeniedException("Not your group"));

        if (n.getStatus() == NoticeStatus.EXPIRED) {
            throw new IllegalStateException("Cannot publish an expired notice.");
        }

        n.setStatus(NoticeStatus.ACTIVE);
        return noticeRepository.save(n);
    }

    public Notice unpublish(Long id, Long groupId) {
        Notice n = noticeRepository.findByIdAndGroupId(id, groupId)
                .orElseThrow(() -> new AccessDeniedException("Not your group"));

        if (n.getStatus() == NoticeStatus.EXPIRED) {
            throw new IllegalStateException("Cannot unpublish an expired notice.");
        }

        n.setStatus(NoticeStatus.DRAFT);
        return noticeRepository.save(n);
    }

    public List<Notice> getDrafts(Long groupId) {
        return noticeRepository.findDraftNotices(groupId);
    }

    public List<Notice> getAllForGroup(Long groupId) {
        return noticeRepository.findByGroupIdOrdered(groupId);
    }

    public Notice getActiveNoticeById(Long id, Long groupId) {

        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notice not found"));

        if (!notice.getGroupId().equals(groupId)) {
            throw new RuntimeException("Forbidden");
        }

        if (!notice.getStatus().equals(NoticeStatus.ACTIVE)) {
            throw new RuntimeException("Not active");
        }

        if (notice.getExpiresAt() != null &&
                notice.getExpiresAt().isBefore(Instant.now())) {
            throw new RuntimeException("Expired");
        }

        return notice;
    }

    public void setVisibility(Long noticeId, Long groupId, List<Long> teamIds) {

        Notice notice = noticeRepository.findByIdAndGroupId(noticeId, groupId)
                .orElseThrow(() -> new RuntimeException("Not your group"));

        noticeTeamVisibilityRepository.deleteByNoticeId(noticeId);

        if (teamIds == null || teamIds.isEmpty()) return;

        for (Long teamId : teamIds) {

            Team team = teamRepository.findByIdAndDeletedAtIsNull(teamId)
                    .orElseThrow();

            if (!team.getGroupId().equals(groupId)) {
                throw new RuntimeException("Forbidden team.");
            }

            NoticeTeamVisibility ntv = new NoticeTeamVisibility();
            ntv.setNoticeId(noticeId);
            ntv.setTeamId(teamId);

            noticeTeamVisibilityRepository.save(ntv);
        }
    }
}