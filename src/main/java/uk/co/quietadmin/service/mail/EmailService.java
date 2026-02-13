package uk.co.quietadmin.service.mail;

import uk.co.quietadmin.domain.notice.Notice;

public interface EmailService {

    void sendVerificationEmail(String toEmail, String verificationToken);

    void sendNoticeExpiryReminder(
            String toEmail,
            String firstName,
            Notice notice
    );
}