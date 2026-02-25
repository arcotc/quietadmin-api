package uk.co.quietadmin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.quietadmin.domain.auth.PasswordResetToken;
import uk.co.quietadmin.domain.auth.PasswordResetTokenRepository;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;
import uk.co.quietadmin.service.mail.EmailService;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserAccountRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.ui.base-url}")
    private String frontendBaseUrl;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void createResetTokenAndSendEmail(UserAccount user) {

        String rawToken = generateToken();
        String tokenHash = sha256(rawToken);

        PasswordResetToken token = PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(60)))
                .build();

        tokenRepository.save(token);

        String resetUrl = frontendBaseUrl + "/reset-password?token=" + rawToken;

        emailService.sendPasswordResetEmail(
                user.getEmail(),
                resetUrl
        );
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {

        String tokenHash = sha256(rawToken);

        PasswordResetToken token = tokenRepository
                .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
                        tokenHash,
                        Instant.now()
                )
                .orElseThrow(() ->
                        new IllegalStateException("Reset link is invalid or expired.")
                );

        UserAccount user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found."));

        // Update password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark token used
        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        tokenRepository.deleteAllByUserId(user.getId());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(
                    digest.digest(value.getBytes())
            );
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash token");
        }
    }
}