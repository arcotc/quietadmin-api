package uk.co.quietadmin.service.mail;

public interface EmailService {
    void sendVerificationEmail(String toEmail, String verificationToken);
}