package uk.co.quietadmin.service.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.notice.Notice;

import java.time.Instant;
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

    @Value("${app.mail.ui-base-url}")
    private String uuBaseUrl;

    /* ======================================================
       VERIFICATION EMAIL
       ====================================================== */

    @Override
    public void sendVerificationEmail(String toEmail, String verificationToken, String formattedExpiry, String firstName, String groupName) {

        String verificationLink = uuBaseUrl + "/verify?token=" + verificationToken;

        String subject = "Verify your QuietAdmin account";

        String html = buildVerificationTemplate(verificationLink, formattedExpiry, firstName, groupName);

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

    private String buildVerificationTemplate(String link, String formattedExpiry, String firstName, String groupName) {

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            <title>Verify your QuietAdmin account</title>
        </head>
        <body style="margin:0;padding:0;background-color:#F7F9FB;font-family:Inter,system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#1F2937;">

            <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 20px;">
                <tr>
                    <td align="center">

                        <!-- Main Card -->
                        <table width="100%%" max-width="520px" cellpadding="0" cellspacing="0"
                               style="background:#ffffff;border-radius:16px;padding:40px 32px;
                                      box-shadow:0 10px 30px rgba(15,23,42,0.05);">

                            <!-- Header -->
                            <tr>
                                <td align="center" style="padding-bottom:24px;">
                                    <h2 style="margin:0;font-weight:600;font-size:22px;">
                                        Welcome to QuietAdmin
                                    </h2>
                                </td>
                            </tr>

                            <!-- Body Text -->
                            <tr>
                                <td style="font-size:15px;line-height:1.6;color:#374151;">
                                    <p style="margin:0 0 18px 0;">
                                        Hi %s,
                                    </p>

                                    <p style="margin:0 0 18px 0;">
                                        Thanks for registering the %s at QuietAdmin.
                                    </p>

                                    <p style="margin:0 0 24px 0;">
                                        Please confirm your email address to activate your account.
                                    </p>
                                </td>
                            </tr>

                            <!-- CTA Button -->
                            <tr>
                                <td align="center" style="padding:10px 0 30px 0;">
                                    <a href="%s"
                                       style="display:inline-block;
                                              padding:14px 26px;
                                              font-weight:500;
                                              font-size:15px;
                                              color:#ffffff;
                                              text-decoration:none;
                                              border-radius:8px;
                                              background:linear-gradient(90deg,#1E6BD6 0%%,#34B67A 100%%);">
                                        Verify your email
                                    </a>
                                </td>
                            </tr>

                            <!-- Expiry Notice -->
                            <tr>
                                <td style="font-size:14px;color:#6B7280;line-height:1.6;">
                                    <p style="margin:0 0 12px 0;">
                                        This link expires at %s.
                                    </p>

                                    <p style="margin:0;">
                                        If the button doesn’t work, copy and paste this into your browser:
                                    </p>

                                    <p style="margin:10px 0 0 0;
                                              word-break:break-all;
                                              color:#1E6BD6;">
                                        %s
                                    </p>
                                </td>
                            </tr>

                        </table>

                        <!-- Footer -->
                        <table width="100%%" max-width="520px" cellpadding="0" cellspacing="0"
                               style="padding:24px 0 0 0;">
                            <tr>
                                <td align="center"
                                    style="font-size:12px;color:#9CA3AF;line-height:1.6;">
                                    QuietAdmin<br/>
                                    No messaging. No feeds. Just clarity.
                                </td>
                            </tr>
                        </table>

                    </td>
                </tr>
            </table>

        </body>
        </html>
        """.formatted(firstName, groupName, link, formattedExpiry, link);
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