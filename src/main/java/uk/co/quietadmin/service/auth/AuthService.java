package uk.co.quietadmin.service.auth;

import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
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
import uk.co.quietadmin.security.TokenHash;
import uk.co.quietadmin.service.mail.EmailService;
import uk.co.quietadmin.web.auth.AuthResponse;
import uk.co.quietadmin.web.auth.LoginThrottleService;
import uk.co.quietadmin.web.auth.SessionResponse;
import uk.co.quietadmin.web.error.ApiException;
import uk.co.quietadmin.web.error.ErrorCode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AuthService {

    @Value("${security.session.idle-seconds:3600}")
    private long sessionIdleSeconds;

    // Stripe checkout config
    @Value("${stripe.price.standard}")
    private String stripePriceId;

    @Value("${app.ui.base-url}")
    private String uiBaseUrl;

    @Value("${app.api.base-url}")
    private String apiBaseUrl;

    @Value("${stripe.trial-days:14}")
    private long trialDays;

    private final UserAccountRepository userRepository;
    private final QaGroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PendingSignupRepository pendingSignupRepository;

    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginThrottleService loginThrottleService;
    private final EmailService emailService;
    private final SubscriptionGuardService subscriptionGuardService;

    private final SecureRandom secureRandom = new SecureRandom();

    private final long refreshDays = 30;

    public AuthService(
            UserAccountRepository userRepository,
            QaGroupRepository groupRepository,
            MembershipRepository membershipRepository,
            RefreshTokenRepository refreshTokenRepository,
            PendingSignupRepository pendingSignupRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginThrottleService loginThrottleService,
            EmailService emailService,
            SubscriptionGuardService subscriptionGuardService
    ) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.pendingSignupRepository = pendingSignupRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginThrottleService = loginThrottleService;
        this.emailService = emailService;
        this.subscriptionGuardService = subscriptionGuardService;
    }

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
            String rawPassword,
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
                .groupName(groupName == null || groupName.isBlank() ? defaultGroupName(normalizedEmail) : groupName)
                .passwordHash(passwordEncoder.encode(rawPassword))
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
                              String requestURI) {

        String normalizedEmail = normalizeEmail(email);

        loginThrottleService.assertLoginAllowed(normalizedEmail, ipAddress);

        UserAccount user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> {
                    loginThrottleService.recordFailure(normalizedEmail, ipAddress);
                    return new IllegalArgumentException("Invalid credentials");
                });

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            loginThrottleService.recordFailure(normalizedEmail, ipAddress);
            throw new IllegalArgumentException("Invalid credentials");
        }

        // ✅ If not verified, do NOT issue tokens (keep the product disciplined)
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new ApiException(
                    403,
                    ErrorCode.EMAIL_NOT_VERIFIED,
                    "Please verify your email address."
            );
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Account not active");
        }

        Optional<Membership> membership = membershipRepository.findByUserId(user.getId());
        if (membership.isEmpty()) throw new IllegalStateException("User has no group membership");

        QaGroup group = groupRepository.findById(membership.get().getGroupId())
                .orElseThrow(() -> new IllegalStateException("Group not found"));

        subscriptionGuardService.assertSubscriptionActive(group);

        loginThrottleService.recordSuccess(normalizedEmail, ipAddress);

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
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (stored.getRevokedAt() != null) {
            revokeAllUserSessions(stored.getUserId(), now);
            throw new IllegalArgumentException("Session revoked (possible replay detected)");
        }

        if (stored.getExpiresAt().isBefore(now)) {
            stored.setRevokedAt(now);
            refreshTokenRepository.save(stored);
            throw new IllegalArgumentException("Refresh token expired");
        }

        String incomingFingerprint = fingerprint(deviceId, userAgent, ipAddress);

        if (!incomingFingerprint.equals(stored.getFingerprintHash())) {
            revokeAllUserSessions(stored.getUserId(), now);
            throw new IllegalArgumentException("Session anomaly detected");
        }

        Instant lastUsed = stored.getLastUsedAt() != null ? stored.getLastUsedAt() : stored.getCreatedAt();
        if (lastUsed != null && lastUsed.isBefore(now.minusSeconds(sessionIdleSeconds))) {
            stored.setRevokedAt(now);
            refreshTokenRepository.save(stored);
            throw new IllegalArgumentException("Session expired due to inactivity");
        }

        UserAccount user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Account not active");
        }

        Membership membership = membershipRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Membership missing"));

        QaGroup group = groupRepository.findById(membership.getGroupId())
                .orElseThrow(() -> new IllegalStateException("Group not found"));

        subscriptionGuardService.assertSubscriptionActive(group);

        stored.setRevokedAt(now);
        stored.setLastUsedAt(now);

        AuthResponse newTokens = issueTokens(user, userAgent, ipAddress, deviceId);
        stored.setReplacedByTokenHash(TokenHash.sha256(newTokens.refreshToken()));
        refreshTokenRepository.save(stored);

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

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return issueTokens(user, userAgent, ipAddress, deviceId);
        }

        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);

        userRepository.save(user);

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
        rt.setExpiresAt(Instant.now().plus(refreshDays, ChronoUnit.DAYS));
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
}
