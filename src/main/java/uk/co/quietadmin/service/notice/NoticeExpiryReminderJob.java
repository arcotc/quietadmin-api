package uk.co.quietadmin.service.notice;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.group.MembershipRepository;
import uk.co.quietadmin.domain.notice.Notice;
import uk.co.quietadmin.domain.notice.NoticeRepository;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;
import uk.co.quietadmin.service.mail.EmailService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeExpiryReminderJob {

    private final NoticeRepository noticeRepository;
    private final MembershipRepository membershipRepository;
    private final UserAccountRepository userRepository;
    private final EmailService emailService;

    @Scheduled(fixedDelay = 60_000)
    public void sendExpiryReminders() {

        Instant now = Instant.now();
        Instant in24Hours = now.plus(24, ChronoUnit.HOURS);

        List<Notice> notices =
                noticeRepository.findNoticesExpiringBetween(now, in24Hours);

        for (Notice notice : notices) {

            // Mark FIRST to prevent double-send if multi-instance
            notice.setExpiryReminderSentAt(Instant.now());
            noticeRepository.save(notice);

            List<Membership> admins =
                    membershipRepository.findByGroupIdAndRole(
                            notice.getGroupId(),
                            "ADMIN"
                    );

            // 👇 THIS IS WHERE YOUR NEW CODE GOES
            List<Long> adminIds = admins.stream()
                    .map(Membership::getUserId)
                    .toList();

            if (adminIds.isEmpty()) {
                continue;
            }

            List<UserAccount> users =
                    userRepository.findByIdIn(adminIds);

            for (UserAccount user : users) {
                emailService.sendNoticeExpiryReminder(
                        user.getEmail(),
                        user.getFirstName(),
                        notice
                );
            }
        }
    }
}