package uk.co.quietadmin.service.mail;

import uk.co.quietadmin.domain.notice.Notice;

import java.time.LocalDate;
import java.util.List;

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

    void sendRotaShareEmail(
            String toEmail,
            String firstName,
            String rotaName,
            LocalDate rotaDate,
            List<RotaShareEmailAssignment> assignments,
            String declineAllLink
    );

    void sendPasswordResetEmail(String toEmail, String resetLink);
}
