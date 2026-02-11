package uk.co.quietadmin.service.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.base-url}")
    private String baseUrl;

    public SmtpEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendVerificationEmail(String toEmail, String verificationToken) {

        String verificationLink =
                baseUrl + "/api/auth/verify?token=" + verificationToken;

        String subject = "Verify your QuietAdmin account";

        String html = buildVerificationTemplate(verificationLink);

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
            throw new IllegalStateException("Failed to send verification email", e);
        }
    }

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
}