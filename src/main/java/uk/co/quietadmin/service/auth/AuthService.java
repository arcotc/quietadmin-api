package uk.co.quietadmin.service.auth;

import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.quietadmin.domain.auth.RefreshToken;
import uk.co.quietadmin.domain.auth.RefreshTokenRepository;
import uk.co.quietadmin.domain.group.*;
import uk.co.quietadmin.domain.signup.PendingSignup;
import uk.co.quietadmin.domain.signup.PendingSignupRepository;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;
import uk.co.quietadmin.domain.user.UserStatus;
import uk.co.quietadmin.security.JwtService;
import uk.co.quietadmin.security.SecurityEventService;
import uk.co.quietadmin.security.TokenHash;
import uk.co.quietadmin.service.mail.EmailService;
import uk.co.quietadmin.web.auth.AuthResponse;
import uk.co.quietadmin.web.auth.LoginThrottleService;
import uk.co.quietadmin.web.auth.SessionResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static uk.co.quietadmin.security.SecurityEventService.getIp;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    @Value("${security.session.idle-seconds:3600}")
    private long sessionIdleSeconds;

    // Stripe checkout config
    @Value("${stripe.priceId}")
    private String stripePriceId;

    @Value("${app.ui.base-url}")
    private String uiBaseUrl;

    @Value("${app.api.base-url}")
    private String apiBaseUrl;

    @Value("${stripe.trial-days:14}")
    private long trialDays;

    @Value("${security.jwt.refresh-expiration-seconds}")
    private long refreshExpirationSeconds;

    private final UserAccountRepository userRepository;
    private final QaGroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PendingSignupRepository pendingSignupRepository;
    private final SecurityEventService securityEventService;

    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginThrottleService loginThrottleService;
    private final EmailService emailService;
    private final SubscriptionGuardService subscriptionGuardService;
    private final MemberService memberService;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * NEW BEHAVIOUR:
     * - Create PendingSignup
     * - Create Stripe Checkout Session (subscription + trial)
     * - Return checkout redirect URL
     * - No user/group created here
     */
    @Transactional
    public AuthResponse register(
            String groupName,
            String email,
//            String rawPassword,
            String firstName,
            String lastName,
            String userAgent,
            String ipAddress,
            String deviceId
    ) {
        String normalizedEmail = normalizeEmail(email);

        userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .ifPresent(u -> { throw new IllegalArgumentException("Email already registered"); });

        String token = generatePublicToken();

        // Create pending signup (expires in 2 hours)
        PendingSignup pending = PendingSignup.builder()
                .token(token)
                .status(PendingSignup.PendingSignupStatus.PENDING_CHECKOUT)
                .email(normalizedEmail)
                .firstName(firstName)
                .lastName(lastName)
                .groupName(groupName == null || groupName.isBlank() ? defaultGroupName(normalizedEmail) : groupName)
//                .passwordHash(passwordEncoder.encode(rawPassword))
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
                .build();

        // Save early so we have a durable token even if Stripe call fails mid-way
        pending = pendingSignupRepository.save(pending);

        // Create Stripe Checkout Session (subscription mode + trial)
        String successUrl = uiBaseUrl + "/signup/success?session_id={CHECKOUT_SESSION_ID}";
        String cancelUrl  = uiBaseUrl + "/signup?cancelled=1";

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)

                // Collect email in your form already; pass it through for consistency
                .setCustomerEmail(normalizedEmail)

                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(stripePriceId)
                                .setQuantity(1L)
                                .build()
                )

                .setSubscriptionData(
                        SessionCreateParams.SubscriptionData.builder()
                                .setTrialPeriodDays(trialDays)
                                // metadata copied onto subscription
                                .putMetadata("pending_signup_token", token)
                                .putMetadata("signup_email", normalizedEmail)
                                .putMetadata("group_name", pending.getGroupName())
                                .build()
                )

                // metadata on the session itself too
                .putMetadata("pending_signup_token", token)

                .build();

        try {
            Session session = Session.create(params);

            pending.setStripeCheckoutSessionId(session.getId());
            pendingSignupRepository.save(pending);

            return AuthResponse.checkoutRedirect(session.getUrl(), normalizedEmail);
        } catch (Exception e) {
            log.error("Stripe Checkout session creation failed for {}", normalizedEmail, e);
            // Leave pending signup in place; it will expire naturally (or you can mark EXPIRED here)
            throw new IllegalStateException("Unable to start checkout. Please try again.");
        }
    }

    @Transactional
    public AuthResponse login(String email,
                              String rawPassword,
                              String userAgent,
                              String ipAddress,
                              String deviceId,
                              HttpServletRequest request) {

        String normalizedEmail = normalizeEmail(email);

        loginThrottleService.assertLoginAllowed(normalizedEmail, ipAddress);

        UserAccount user = userRepository
                .findByEmailAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> {
                    log.info("Recording login failure for {} {}", normalizedEmail, ipAddress);
                    loginThrottleService.recordFailure(normalizedEmail, ipAddress);
                    securityEventService.authFailure(normalizedEmail, ipAddress, "invalid_credentials");
                    return new IllegalArgumentException("Invalid credentials");
                });

        // If no password yet → must activate first
        if (user.getPasswordHash() == null) {
            log.info("Recording login failure for {} {}", normalizedEmail, ipAddress);
            loginThrottleService.recordFailure(normalizedEmail, ipAddress);
            securityEventService.authFailure(normalizedEmail, ipAddress, "must_activate_first");
            return AuthResponse.passwordSetupRequired(user.getEmail());
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            log.info("Recording login failure for {} {}", normalizedEmail, ipAddress);
            loginThrottleService.recordFailure(normalizedEmail, ipAddress);
            securityEventService.authFailure(normalizedEmail, ipAddress, "invalid_credentials");
            return AuthResponse.failure();
//            throw new IllegalArgumentException("Invalid credentials");
        }

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            log.info("Recording login failure for {} {}", normalizedEmail, ipAddress);
            loginThrottleService.recordFailure(normalizedEmail, ipAddress);
            securityEventService.authFailure(normalizedEmail, ipAddress, "email_not_verified");
            return AuthResponse.verificationRequired(user.getEmail());
        }

        if (user.getUserStatus() != UserStatus.ACTIVE) {
            log.info("Recording login failure for {} {}", normalizedEmail, ipAddress);
            loginThrottleService.recordFailure(normalizedEmail, ipAddress);
            securityEventService.authFailure(normalizedEmail, ipAddress, "account_not_active");
            throw new IllegalArgumentException("Account not active");
        }

        Optional<Membership> membership = membershipRepository.findByUserId(user.getId());

        if (membership.isEmpty()) {
            log.info("Recording login failure for {} {}", normalizedEmail, ipAddress);
            securityEventService.authFailure(normalizedEmail, ipAddress, "user_has_no_group_membership");
            throw new IllegalStateException("User has no group membership");
        }

        QaGroup group = groupRepository.findById(membership.get().getGroupId())
                .orElseThrow(() -> {
                    log.info("Recording login failure for {} {}", normalizedEmail, ipAddress);
                    securityEventService.authFailure(
                            normalizedEmail,
                            ipAddress,
                            "group_not_found_for_membership"
                    );
                    return new IllegalStateException("Group not found");
                });

        subscriptionGuardService.assertSubscriptionActive(group, normalizedEmail, ipAddress);

        loginThrottleService.recordSuccess(normalizedEmail, ipAddress);
        securityEventService.authSuccess(user.getId(), user.getEmail(), ipAddress);

        return issueTokens(user, userAgent, ipAddress, deviceId);
    }

    @Transactional
    public AuthResponse refresh(
            String rawRefreshToken,
            String userAgent,
            String ipAddress,
            String deviceId
    ) {

        Instant now = Instant.now();
        String hash = TokenHash.sha256(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository
                .findByTokenHashAndRevokedAtIsNull(hash)
                .orElseThrow(() -> {
                    logSessionAnomaly(null, ipAddress, "invalid_token");
//                    securityEventService.sessionAnomaly(stored.getUserId(), ipAddress, "invalid_token");
                    return new IllegalArgumentException("Invalid refresh token");
                });

        if (stored.getRevokedAt() != null) {
            revokeAllUserSessions(stored.getUserId(), now);
            logSessionAnomaly(stored.getUserId(), ipAddress, "possible_replay_detected");
            securityEventService.sessionAnomaly(stored.getUserId(), ipAddress, "possible_replay_detected");
            throw new IllegalArgumentException("Session revoked (possible replay detected)");
        }

        if (stored.getExpiresAt().isBefore(now)) {
            stored.setRevokedAt(now);
            refreshTokenRepository.save(stored);
            logSessionAnomaly(stored.getUserId(), ipAddress, "refresh_token_expired");
            securityEventService.sessionAnomaly(stored.getUserId(), ipAddress, "refresh_token_expired");
            throw new IllegalArgumentException("Refresh token expired");
        }

        String incomingFingerprint = fingerprint(deviceId, userAgent, ipAddress);

        if (!incomingFingerprint.equals(stored.getFingerprintHash())) {
            revokeAllUserSessions(stored.getUserId(), now);
            logSessionAnomaly(stored.getUserId(), ipAddress, "fingerprint_mismatch");
            securityEventService.sessionAnomaly(stored.getUserId(), ipAddress, "fingerprint_mismatch");
            throw new IllegalArgumentException("Session anomaly detected");
        }

        Instant lastUsed = stored.getLastUsedAt() != null ? stored.getLastUsedAt() : stored.getCreatedAt();
        if (lastUsed != null && lastUsed.isBefore(now.minusSeconds(sessionIdleSeconds))) {
            stored.setRevokedAt(now);
            refreshTokenRepository.save(stored);
            logSessionAnomaly(stored.getUserId(), ipAddress, "session_expired");
            securityEventService.sessionAnomaly(stored.getUserId(), ipAddress, "session_expired");
            throw new IllegalArgumentException("Session expired due to inactivity");
        }

        if (stored.getReplacedByTokenHash() != null) {
            securityEventService.sessionAnomaly(
                    stored.getUserId(),
                    ipAddress,
                    "refresh_token_reuse_detected"
            );

            revokeAllUserSessions(stored.getUserId(), now);
            throw new IllegalArgumentException("Session compromised");
        }

        if (!stored.getIpAddress().equals(ipAddress)) {
            securityEventService.sessionAnomaly(
                    stored.getUserId(),
                    ipAddress,
                    "ip_changed_during_session"
            );
        }

        UserAccount user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> {
                    log.warn("DATA_INCONSISTENCY userId={} reason=user_not_found", stored.getUserId());
                    securityEventService.sessionAnomaly(stored.getUserId(), ipAddress, "user_not_found");
                    return new IllegalArgumentException("User not found");
                });

        if (user.getUserStatus() != UserStatus.ACTIVE) {
            log.warn("DATA_INCONSISTENCY userId={} reason=account_not_active", stored.getUserId());
            securityEventService.sessionAnomaly(stored.getUserId(), ipAddress, "account_not_active");
            throw new IllegalArgumentException("Account not active");
        }

        Membership membership = membershipRepository.findByUserId(user.getId())
                .orElseThrow(() -> {
                    log.warn("DATA_INCONSISTENCY userId={} reason=membership_missing", stored.getUserId());
                    securityEventService.sessionAnomaly(stored.getUserId(), ipAddress, "membership_missing");
                    return new IllegalStateException("Membership missing");
                });

        QaGroup group = groupRepository.findById(membership.getGroupId())
                .orElseThrow(() -> {
                    log.warn("DATA_INCONSISTENCY userId={} reason=group_missing", stored.getUserId());
                    securityEventService.sessionAnomaly(stored.getUserId(), ipAddress, "group_not_found");
                    return new IllegalStateException("Group not found");
                });

        subscriptionGuardService.assertSubscriptionActive(group, user.getEmail(), ipAddress);

        stored.setRevokedAt(now);
        stored.setLastUsedAt(now);

        AuthResponse newTokens = issueTokens(user, userAgent, ipAddress, deviceId);
        stored.setReplacedByTokenHash(TokenHash.sha256(newTokens.refreshToken()));
        refreshTokenRepository.save(stored);

        securityEventService.event("AUTH_REFRESH_SUCCESS", Map.of(
                "userId", user.getId(),
                "ip", ipAddress
        ));

        return newTokens;
    }

    @Transactional
    public void logoutAll(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(Long userId, String currentRefreshToken) {

        String currentHash = currentRefreshToken != null
                ? TokenHash.sha256(currentRefreshToken)
                : null;

        return refreshTokenRepository
                .findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(rt -> new SessionResponse(
                        rt.getId(),
                        rt.getCreatedAt(),
                        rt.getLastUsedAt(),
                        rt.getExpiresAt(),
                        rt.getUserAgent(),
                        rt.getIpAddress(),
                        currentHash != null && currentHash.equals(rt.getTokenHash())
                ))
                .toList();
    }

    @Transactional
    public void revokeSession(Long userId, Long sessionId) {
        int updated = refreshTokenRepository.revokeOne(sessionId, userId, Instant.now());
        if (updated == 0) throw new IllegalArgumentException("Session not found");
    }

    @Transactional
    public AuthResponse verifyEmail(String rawToken,
                                    String userAgent,
                                    String ipAddress,
                                    String deviceId) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Invalid verification token");
        }

        String hashed = TokenHash.sha256(rawToken);

        UserAccount user = userRepository
                .findByEmailVerificationTokenAndDeletedAtIsNull(hashed)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (user.getEmailVerificationExpiresAt() == null ||
                user.getEmailVerificationExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Verification token expired");
        }

        user.setEmailVerified(true);

        user.setUserStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        memberService.completeMembershipAfterActivation(user);

        return issueTokens(user, userAgent, ipAddress, deviceId);
    }

    @Transactional
    public void resendVerificationEmail(String email) {

        if (email == null || email.isBlank()) {
            return; // silent
        }

        String normalizedEmail = normalizeEmail(email);

        Optional<UserAccount> optionalUser =
                userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail);

        if (optionalUser.isEmpty()) {
            return; // silent (prevent enumeration)
        }

        UserAccount user = optionalUser.get();

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return; // already verified — silent
        }

        // Regenerate verification token
        String verificationRaw = generatePublicToken();
        String verificationHash = TokenHash.sha256(verificationRaw);

        user.setEmailVerificationToken(verificationHash);
        user.setEmailVerificationExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

        userRepository.save(user);

        emailService.sendVerificationEmail(
                user.getEmail(),
                verificationRaw,
                user.getEmailVerificationExpiresAt().toString(),
                user.getFirstName(),
                "QuietAdmin"
        );
    }

    @Transactional
    public AuthResponse activateAccount(
            String email,
            String rawPassword,
            String userAgent,
            String ipAddress,
            String deviceId
    ) {

        UserAccount user = userRepository
                .findByEmailAndDeletedAtIsNull(normalizeEmail(email))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalStateException("Email not verified");
        }

        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setUserStatus(UserStatus.ACTIVE);

        userRepository.save(user);

        memberService.completeMembershipAfterActivation(user);

        return issueTokens(user, userAgent, ipAddress, deviceId);
    }

    @Transactional
    public AuthResponse setPassword(
            String rawToken,
            String rawPassword,
            String userAgent,
            String ip,
            String deviceId
    ) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Invalid token");
        }

        String hashed = TokenHash.sha256(rawToken);

        UserAccount user = userRepository
                .findByEmailVerificationTokenAndDeletedAtIsNull(hashed)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));

        if (user.getEmailVerificationExpiresAt() == null ||
                user.getEmailVerificationExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("Token expired");
        }

        if (user.getPasswordHash() != null) {
            throw new IllegalStateException("Password already set");
        }

        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password too short");
        }

        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setEmailVerified(true);
        user.setUserStatus(UserStatus.ACTIVE);

        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiresAt(null);

        userRepository.save(user);

        memberService.completeMembershipAfterActivation(user);

        return issueTokens(user, userAgent, ip, deviceId);
    }

    // =========================================================

    private AuthResponse issueTokens(UserAccount user, String userAgent, String ipAddress, String deviceId) {

        JwtService.JwtToken access = jwtService.createAccessToken(user.getEmail());
        String refreshRaw = generateRefreshToken();

        persistRefreshToken(user.getId(), refreshRaw, userAgent, ipAddress, deviceId);

        return AuthResponse.success(
                access.token(),
                refreshRaw,
                access.expiresAt(),
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName()
        );
    }

    private void persistRefreshToken(Long userId, String rawToken, String userAgent, String ipAddress, String deviceId) {

        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setTokenHash(TokenHash.sha256(rawToken));
        rt.setExpiresAt(Instant.now().plusSeconds(refreshExpirationSeconds));
        rt.setLastUsedAt(Instant.now());
        rt.setUserAgent(userAgent);
        rt.setIpAddress(ipAddress);
        rt.setDeviceId(deviceId);
        rt.setFingerprintHash(fingerprint(deviceId, userAgent, ipAddress));

        refreshTokenRepository.save(rt);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generatePublicToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String email) {
        if (email == null) throw new IllegalArgumentException("Email is required");
        return email.trim().toLowerCase();
    }

    private String defaultGroupName(String email) {
        return email.split("@")[0] + "'s Group";
    }

    private void revokeAllUserSessions(Long userId, Instant now) {
        refreshTokenRepository
                .findByUserIdAndRevokedAtIsNull(userId)
                .forEach(token -> {
                    token.setRevokedAt(now);
                    refreshTokenRepository.save(token);
                });
    }

    private String fingerprint(String deviceId, String userAgent, String ipAddress) {

        String safeDevice = deviceId != null ? deviceId : "unknown-device";
        String safeAgent = userAgent != null ? userAgent : "unknown-agent";
        String safeIp = ipAddress != null ? ipAddress : "unknown-ip";

        return TokenHash.sha256(safeDevice + "|" + safeAgent + "|" + safeIp);
    }

    private void logSessionAnomaly(Long userId, String ip, String reason) {
        securityEventService.sessionAnomaly(userId, ip, reason);
    }
}
