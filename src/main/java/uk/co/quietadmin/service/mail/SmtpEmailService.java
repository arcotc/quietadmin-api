package uk.co.quietadmin.service.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.notice.Notice;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.ui-base-url}")
    private String uiBaseUrl;

    /* ======================================================
       VERIFICATION EMAIL (SELF-SIGNUP)
       ====================================================== */

    @Override
    public void sendVerificationEmail(
            String toEmail,
            String verificationToken,
            String formattedExpiry,
            String firstName,
            String groupName
    ) {

        String verificationLink = uiBaseUrl + "/verify?token=" + verificationToken;

        String subject = "Verify your QuietAdmin account";

        String html = buildVerificationTemplate(
                verificationLink,
                formattedExpiry,
                firstName,
                groupName
        );

        sendHtmlEmail(toEmail, subject, html);
    }

    /* ======================================================
       GROUP INVITATION EMAIL
       ====================================================== */

    @Override
    public void sendGroupInvitationEmail(
            String toEmail,
            String verificationToken,
            String formattedExpiry,
            String groupName,
            String invitedByName
    ) {

        String verificationLink = uiBaseUrl + "/verify?token=" + verificationToken;

        String subject = "You’ve been invited to join " + groupName;

        String html = buildInvitationTemplate(
                verificationLink,
                formattedExpiry,
                groupName,
                invitedByName
        );

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
       ROTA SHARE EMAIL
       ====================================================== */

    @Override
    public void sendRotaShareEmail(
            String toEmail,
            String firstName,
            String rotaName,
            LocalDate rotaDate,
            List<RotaShareEmailAssignment> assignments,
            String declineAllLink
    ) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy");
        String formattedDate = rotaDate.format(formatter);

        String subject = "Your assignment — " + rotaName + ", " + formattedDate;

        String html = buildRotaShareTemplate(
                firstName,
                rotaName,
                formattedDate,
                assignments,
                declineAllLink
        );

        sendHtmlEmail(toEmail, subject, html);
    }

    /* ======================================================
       INTERNAL SEND METHOD
       ====================================================== */

    private void sendHtmlEmail(String toEmail, String subject, String html) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);

        } catch (MessagingException | MailException e) {
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

    /* ======================================================
       INVITATION TEMPLATE
       ====================================================== */

    private String buildInvitationTemplate(
            String link,
            String formattedExpiry,
            String groupName,
            String invitedByName
    ) {

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            <title>Group Invitation</title>
        </head>
        <body style="margin:0;padding:0;background:#F7F9FB;
                     font-family:Inter,system-ui,-apple-system,sans-serif;
                     color:#1F2937;">

        <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 20px;">
            <tr>
                <td align="center">

                    <table width="100%%" max-width="520px"
                           style="background:#ffffff;border-radius:16px;
                                  padding:40px 32px;
                                  box-shadow:0 10px 30px rgba(15,23,42,0.05);">

                        <tr>
                            <td align="center" style="padding-bottom:24px;">
                                <h2 style="margin:0;font-weight:600;">
                                    You’ve been invited
                                </h2>
                            </td>
                        </tr>

                        <tr>
                            <td style="font-size:15px;line-height:1.6;color:#374151;">

                                <p style="margin:0 0 18px 0;">
                                    %s has invited you to join:
                                </p>

                                <p style="margin:0 0 24px 0;
                                          font-weight:600;
                                          font-size:16px;">
                                    %s
                                </p>

                                <p style="margin:0 0 24px 0;">
                                    QuietAdmin provides calm,
                                    structured group administration —
                                    rotas, notices and shared links.
                                </p>

                            </td>
                        </tr>

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
                                    Accept invitation
                                </a>
                            </td>
                        </tr>

                        <tr>
                            <td style="font-size:14px;color:#6B7280;line-height:1.6;">
                                <p style="margin:0 0 12px 0;">
                                    This link expires at %s.
                                </p>
                                <p style="margin:0;">
                                    If the button doesn’t work, copy and paste:
                                </p>
                                <p style="margin:10px 0 0 0;
                                          word-break:break-all;
                                          color:#1E6BD6;">
                                    %s
                                </p>
                            </td>
                        </tr>

                    </table>

                    <table width="100%%" max-width="520px"
                           style="padding:24px 0 0 0;">
                        <tr>
                            <td align="center"
                                style="font-size:12px;color:#9CA3AF;">
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
        """.formatted(
                invitedByName,
                groupName,
                link,
                formattedExpiry,
                link
        );
    }

    /* ======================================================
       NOTICE TEMPLATE (unchanged)
       ====================================================== */

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
                    <p style="font-size:16px;font-weight:600;">%s</p>
                    <hr style="margin:30px 0;border:none;border-top:1px solid #eee;"/>
                    <p style="font-size:12px;color:#666;">
                        QuietAdmin — No messaging. No feeds. Just clarity.
                    </p>
                </body>
                </html>
                """.formatted(greeting, noticeTitle, expiry);
    }

    private String buildRotaShareTemplate(
            String firstName,
            String rotaName,
            String formattedDate,
            List<RotaShareEmailAssignment> assignments,
            String declineAllLink
    ) {

        String greeting = (firstName != null && !firstName.isBlank())
                ? "Hi " + firstName + ","
                : "Hello,";

        String heading = assignments.size() == 1
                ? "Your rota assignment"
                : "Your rota assignments";

        String intro = assignments.size() == 1
                ? "You have been assigned to the following role:"
                : "You have been assigned to the following roles:";

        // --- assignment rows ---
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < assignments.size(); i++) {
            RotaShareEmailAssignment a = assignments.get(i);
            String borderStyle = i == 0
                    ? "border-top:1px solid #F3F4F6;"
                    : "border-top:1px solid #F3F4F6;";
            rows.append(
                "<tr><td style=\"padding:12px 0;" + borderStyle + "\">" +
                "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>" +
                "<td style=\"font-size:15px;color:#1F2937;font-weight:500;\">" +
                escapeHtml(a.roleName()) +
                "</td>" +
                "<td align=\"right\">" +
                "<a href=\"" + a.declineLink() + "\" " +
                "style=\"font-size:13px;color:#6B7280;text-decoration:underline;\">" +
                "Can&#39;t do this</a>" +
                "</td></tr></table></td></tr>"
            );
        }

        // --- decline all section (only for multiple positions) ---
        String declineAllSection = "";
        if (assignments.size() > 1) {
            declineAllSection =
                "<tr><td style=\"padding:28px 0 8px 0;border-top:1px solid #F3F4F6;\">" +
                "<p style=\"margin:0 0 16px 0;font-size:14px;color:#6B7280;text-align:center;\">" +
                "Can&#39;t make it at all?" +
                "</p>" +
                "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>" +
                "<td align=\"center\">" +
                "<a href=\"" + declineAllLink + "\" " +
                "style=\"display:inline-block;padding:12px 24px;font-weight:500;" +
                "font-size:14px;color:#ffffff;text-decoration:none;border-radius:8px;" +
                "background:linear-gradient(90deg,#1E6BD6 0%,#34B67A 100%);\">" +
                "I can&#39;t make it at all" +
                "</a>" +
                "</td></tr></table>" +
                "<p style=\"margin:12px 0 0 0;font-size:13px;color:#9CA3AF;text-align:center;\">" +
                "This will remove you from all positions on this rota. No account needed." +
                "</p>" +
                "</td></tr>";
        }

        return
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "<meta charset=\"UTF-8\"/>" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>" +
            "<title>Your rota assignment</title>" +
            "</head>" +
            "<body style=\"margin:0;padding:0;background:#F7F9FB;" +
            "font-family:Inter,system-ui,-apple-system,sans-serif;color:#1F2937;\">" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding:40px 20px;\">" +
            "<tr><td align=\"center\">" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"" +
            " style=\"max-width:520px;background:#ffffff;border-radius:16px;" +
            "padding:40px 32px;box-shadow:0 10px 30px rgba(15,23,42,0.05);\">" +
            "<tr><td align=\"center\" style=\"padding-bottom:24px;\">" +
            "<h2 style=\"margin:0;font-weight:600;\">" + escapeHtml(heading) + "</h2>" +
            "</td></tr>" +
            "<tr><td style=\"font-size:15px;line-height:1.6;color:#374151;\">" +
            "<p style=\"margin:0 0 6px 0;\">" + escapeHtml(greeting) + "</p>" +
            "<p style=\"margin:0 0 4px 0;\"><strong>" + escapeHtml(rotaName) + "</strong></p>" +
            "<p style=\"margin:0 0 20px 0;color:#6B7280;\">" + escapeHtml(formattedDate) + "</p>" +
            "<p style=\"margin:0 0 8px 0;\">" + escapeHtml(intro) + "</p>" +
            "</td></tr>" +
            rows +
            declineAllSection +
            "<tr><td style=\"padding-top:24px;font-size:13px;color:#9CA3AF;\">" +
            "Clicking &#39;Can&#39;t do this&#39; will update the rota. No account needed." +
            "</td></tr>" +
            "</table>" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding:24px 0 0 0;\">" +
            "<tr><td align=\"center\" style=\"font-size:12px;color:#9CA3AF;\">" +
            "QuietAdmin<br/>No messaging. No feeds. Just clarity." +
            "</td></tr></table>" +
            "</td></tr></table>" +
            "</body></html>";
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("’", "&#39;");
    }

    /* ======================================================
   PASSWORD RESET EMAIL
   ====================================================== */

    @Override
    public void sendPasswordResetEmail(
            String toEmail,
            String resetLink
    ) {

        String subject = "Reset your QuietAdmin password";

        String html = buildPasswordResetTemplate(resetLink);

        sendHtmlEmail(toEmail, subject, html);
    }

    private String buildPasswordResetTemplate(String link) {

        return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
        <title>Reset your password</title>
    </head>
    <body style="margin:0;padding:0;background:#F7F9FB;
                 font-family:Inter,system-ui,-apple-system,sans-serif;
                 color:#1F2937;">

        <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 20px;">
            <tr>
                <td align="center">

                    <!-- Main Card -->
                    <table width="100%%" max-width="520px"
                           style="background:#ffffff;border-radius:16px;
                                  padding:40px 32px;
                                  box-shadow:0 10px 30px rgba(15,23,42,0.05);">

                        <!-- Header -->
                        <tr>
                            <td align="center" style="padding-bottom:24px;">
                                <h2 style="margin:0;font-weight:600;">
                                    Reset your password
                                </h2>
                            </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                            <td style="font-size:15px;line-height:1.6;color:#374151;">

                                <p style="margin:0 0 18px 0;">
                                    We received a request to reset your QuietAdmin password.
                                </p>

                                <p style="margin:0 0 24px 0;">
                                    Click the button below to choose a new password.
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
                                    Reset password
                                </a>
                            </td>
                        </tr>

                        <!-- Fallback Link -->
                        <tr>
                            <td style="font-size:14px;color:#6B7280;line-height:1.6;">
                                <p style="margin:0 0 12px 0;">
                                    This link expires in 60 minutes.
                                </p>

                                <p style="margin:0;">
                                    If the button doesn’t work, copy and paste this link:
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
                    <table width="100%%" max-width="520px"
                           style="padding:24px 0 0 0;">
                        <tr>
                            <td align="center"
                                style="font-size:12px;color:#9CA3AF;">
                                If you didn’t request this, you can safely ignore this email.<br/><br/>
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
    """.formatted(link, link);
    }
}
