package uk.co.quietadmin.service.notice;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.notice.Notice;
import uk.co.quietadmin.domain.notice.NoticeRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public List<Notice> getActiveNotices(Long groupId) {
        Instant now = Instant.now();

        List<Notice> notices =
                noticeRepository.findAll().stream()
                        .filter(n -> n.getGroupId().equals(groupId))
                        .filter(n -> n.getExpiresAt() == null || n.getExpiresAt().isAfter(now))
                        .toList();

        return notices.stream()
                .sorted(Comparator
                        .comparing((Notice n) -> n.getExpiresAt() == null ? Instant.MAX : n.getExpiresAt())
                        .thenComparing(Notice::getCreatedAt).reversed())
                .toList();
    }

    public Notice createNotice(Notice notice) {
        return noticeRepository.save(notice);
    }

    public void deleteNotice(Long id, Long groupId) {
        Notice n = noticeRepository.findById(id)
                .orElseThrow();

        if (!n.getGroupId().equals(groupId)) {
            throw new RuntimeException("Not allowed");
        }

        noticeRepository.delete(n);
    }
}