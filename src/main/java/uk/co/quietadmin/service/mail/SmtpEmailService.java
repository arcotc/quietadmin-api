package uk.co.quietadmin.service.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.notice.Notice;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.base-url}")
    private String baseUrl;

    /* ======================================================
       VERIFICATION EMAIL
       ====================================================== */

    @Override
    public void sendVerificationEmail(String toEmail, String verificationToken) {

        String verificationLink =
                baseUrl + "/api/auth/verify?token=" + verificationToken;

        String subject = "Verify your QuietAdmin account";

        String html = buildVerificationTemplate(verificationLink);

        sendHtmlEmail(toEmail, subject, html);
    }

    /* ======================================================
       NOTICE EXPIRY REMINDER
       ====================================================== */

    @Override
    public void sendNoticeExpiryReminder(
            String toEmail,
            String firstName,
            Notice notice
    ) {

        String subject = "Notice expiring tomorrow";

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'at' HH:mm")
                        .withZone(ZoneId.of("Europe/London"));

        String formattedExpiry =
                notice.getExpiresAt() != null
                        ? formatter.format(notice.getExpiresAt())
                        : "soon";

        String html = buildExpiryReminderTemplate(
                firstName,
                notice.getTitle(),
                formattedExpiry
        );

        sendHtmlEmail(toEmail, subject, html);
    }

    /* ======================================================
       INTERNAL SEND METHOD
       ====================================================== */

    private void sendHtmlEmail(
            String toEmail,
            String subject,
            String html
    ) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);

        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to send email", e);
        }
    }

    /* ======================================================
       TEMPLATES
       ====================================================== */

    private String buildVerificationTemplate(String link) {

        return """
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <h2>Welcome to QuietAdmin</h2>
                    <p>Please verify your email address by clicking the button below:</p>
                    <p>
                        <a href="%s"
                           style="background-color:#111827;color:white;
                                  padding:12px 20px;text-decoration:none;
                                  border-radius:6px;">
                            Verify Email
                        </a>
                    </p>
                    <p>This link expires in 24 hours.</p>
                </body>
                </html>
                """.formatted(link);
    }

    private String buildExpiryReminderTemplate(
            String firstName,
            String noticeTitle,
            String expiry
    ) {

        String greeting =
                (firstName != null && !firstName.isBlank())
                        ? "Hi " + firstName + ","
                        : "Hello,";

        return """
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <h2>Notice Expiring Soon</h2>

                    <p>%s</p>

                    <p>Your notice <strong>"%s"</strong> is scheduled to expire on:</p>

                    <p style="font-size:16px;font-weight:600;">
                        %s
                    </p>

                    <p>
                        If it is still relevant, you can extend or update it
                        from the QuietAdmin dashboard.
                    </p>

                    <hr style="margin:30px 0;border:none;border-top:1px solid #eee;"/>

                    <p style="font-size:12px;color:#666;">
                        QuietAdmin — No messaging. No feeds. Just clarity.
                    </p>
                </body>
                </html>
                """.formatted(
                greeting,
                noticeTitle,
                expiry
        );
    }
}