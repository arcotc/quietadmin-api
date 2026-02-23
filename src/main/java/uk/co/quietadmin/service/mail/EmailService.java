package uk.co.quietadmin.service.mail;

import uk.co.quietadmin.domain.notice.Notice;

import java.time.Instant;

public interface EmailService {

    void sendNoticeExpiryReminder(
            String toEmail,
            String firstName,
            Notice notice
    );

    void sendVerificationEmail(String email, String verificationRaw, String formattedExpiry, String firstName, String groupName);

    void sendGroupInvitationEmail(
            String toEmail,
            String verificationToken,
            String formattedExpiry,
            String groupName,
            String invitedByName
    );
}