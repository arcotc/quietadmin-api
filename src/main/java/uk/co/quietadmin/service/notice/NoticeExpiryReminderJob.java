package uk.co.quietadmin.service.notice;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.group.MembershipRepository;
import uk.co.quietadmin.domain.group.QaGroup;
import uk.co.quietadmin.domain.group.QaGroupRepository;
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
    private final QaGroupRepository qaGroupRepository;

    @Scheduled(fixedDelay = 60_000)
    public void sendExpiryReminders() {

        Instant now = Instant.now();

        // Find all groups that have reminders enabled
        List<QaGroup> groups = qaGroupRepository.findByExpiryReminderHoursIsNotNull();

        for (QaGroup group : groups) {

            Integer hours = group.getExpiryReminderHours();

            if (hours == null || hours <= 0) continue;

            Instant reminderCutoff =
                    now.plus(hours, ChronoUnit.HOURS);

            List<Notice> notices =
                    noticeRepository.findNoticesExpiringBetweenForGroup(
                            group.getId(),
                            now,
                            reminderCutoff
                    );

            for (Notice notice : notices) {

                notice.setExpiryReminderSentAt(now);
                noticeRepository.save(notice);

                notifyAdmins(group.getId(), notice);
            }
        }
    }

    private void notifyAdmins(Long groupId, Notice notice) {

        List<Membership> admins =
                membershipRepository.findByGroupIdAndRole(
                        groupId,
                        "ADMIN"
                );

        if (admins.isEmpty()) return;

        List<Long> adminIds = admins.stream()
                .map(Membership::getUserId)
                .toList();

        List<UserAccount> users =
                userRepository.findByIdIn(adminIds);

        for (UserAccount user : users) {

            // Skip deleted or inactive users defensively
            if (!"ACTIVE".equals(user.getUserStatus())) continue;

            emailService.sendNoticeExpiryReminder(
                    user.getEmail(),
                    user.getFirstName(),
                    notice
            );
        }
    }
}