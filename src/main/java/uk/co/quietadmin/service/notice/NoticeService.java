package uk.co.quietadmin.service.notice;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.notice.Notice;
import uk.co.quietadmin.domain.notice.NoticeRepository;

import org.springframework.security.access.AccessDeniedException;
import uk.co.quietadmin.domain.notice.NoticeStatus;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public List<Notice> getActiveNotices(Long groupId) {
        return noticeRepository.findActiveNotices(groupId, Instant.now());
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
}