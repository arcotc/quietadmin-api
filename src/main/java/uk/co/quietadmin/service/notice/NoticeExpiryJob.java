package uk.co.quietadmin.service.notice;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.notice.Notice;
import uk.co.quietadmin.domain.notice.NoticeRepository;
import uk.co.quietadmin.domain.notice.NoticeStatus;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeExpiryJob {

    private final NoticeRepository noticeRepository;

    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    public void expireNotices() {
        Instant now = Instant.now();
        List<Notice> toExpire = noticeRepository.findActiveButExpired(now);

        for (Notice n : toExpire) {
            n.setStatus(NoticeStatus.EXPIRED);
            noticeRepository.save(n);
        }
    }
}