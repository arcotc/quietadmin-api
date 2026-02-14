package uk.co.quietadmin.service.notice;

import jakarta.transaction.Transactional;
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

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void expireNotices() {
        noticeRepository.bulkExpire(Instant.now());
    }
}