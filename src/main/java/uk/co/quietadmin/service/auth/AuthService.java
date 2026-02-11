package uk.co.quietadmin.service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.quietadmin.domain.auth.RefreshToken;
import uk.co.quietadmin.domain.auth.RefreshTokenRepository;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.group.MembershipRepository;
import uk.co.quietadmin.domain.group.QaGroup;
import uk.co.quietadmin.domain.group.QaGroupRepository;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;
import uk.co.quietadmin.domain.user.UserStatus;
import uk.co.quietadmin.security.JwtService;
import uk.co.quietadmin.security.TokenHash;
import uk.co.quietadmin.web.auth.AuthResponse;
import uk.co.quietadmin.web.auth.LoginThrottleService;
import uk.co.quietadmin.web.auth.SessionResponse;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

@Service
public class AuthService {
    @Value("${security.session.idle-seconds:3600}")
    private long sessionIdleSeconds;

    private final UserAccountRepository userRepository;
    private final QaGroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginThrottleService loginThrottleService;

    private final SecureRandom secureRandom = new SecureRandom();

    // keep in config if you like
    private final long trialDays = 14;

    // should match property (or inject it)
    private final long refreshDays = 30;

    public AuthService(
            UserAccountRepository userRepository,
            QaGroupRepository groupRepository,
            MembershipRepository membershipRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService, LoginThrottleService loginThrottleService
    ) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginThrottleService = loginThrottleService;
    }

    @Transactional
    public AuthResponse register(String email,
                                 String rawPassword,
                                 String userAgent,
                                 String ipAddress) {

        String normalizedEmail = normalizeEmail(email);

        userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .ifPresent(u -> {
                    throw new IllegalArgumentException("Email already registered");
                });

        // 1️⃣ Create user
        UserAccount user = new UserAccount();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);

        // 2️⃣ Create group
        QaGroup group = new QaGroup();
        group.setName(defaultGroupName(normalizedEmail));
        group.setSlug(generateSlug(normalizedEmail));
        group.setCreatedBy(user.getId());
        group.setTrialEndsAt(Instant.now().plus(trialDays, ChronoUnit.DAYS));
        group = groupRepository.save(group);

        // 3️⃣ Create membership
        Membership membership = new Membership();
        membership.setUserId(user.getId());
        membership.setGroupId(group.getId());
        membership.setRole("ADMIN");
        membershipRepository.save(membership);

        // 4️⃣ Issue tokens with device context
        return issueTokens(user, userAgent, ipAddress);
    }

    @Transactional
    public AuthResponse login(String email,
                              String rawPassword,
                              String userAgent,
                              String ipAddress) {
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

        loginThrottleService.recordSuccess(normalizedEmail, ipAddress);

        return issueTokens(user, userAgent, ipAddress);
    }

    @Transactional
    public AuthResponse refresh(
            String rawRefreshToken,
            String userAgent,
            String ipAddress
    ) {

        Instant now = Instant.now();
        String hash = TokenHash.sha256(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        // ---------------------------------------
        // 1️⃣ Replay detection (critical security)
        // ---------------------------------------
        if (stored.getRevokedAt() != null) {

            // If token already revoked → possible replay attack
            revokeAllUserSessions(stored.getUserId(), now);

            throw new IllegalArgumentException("Session revoked (possible replay detected)");
        }

        // ---------------------------------------
        // 2️⃣ Expiry check
        // ---------------------------------------
        if (stored.getExpiresAt().isBefore(now)) {
            stored.setRevokedAt(now);
            refreshTokenRepository.save(stored);
            throw new IllegalArgumentException("Refresh token expired");
        }

        // ---------------------------------------
        // 3️⃣ Idle timeout check
        // ---------------------------------------
        Instant lastUsed = stored.getLastUsedAt() != null
                ? stored.getLastUsedAt()
                : stored.getCreatedAt();

        if (lastUsed != null && lastUsed.isBefore(now.minusSeconds(sessionIdleSeconds))) {
            stored.setRevokedAt(now);
            refreshTokenRepository.save(stored);
            throw new IllegalArgumentException("Session expired due to inactivity");
        }

        // ---------------------------------------
        // 4️⃣ Load user
        // ---------------------------------------
        UserAccount user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Account not active");
        }

        // ---------------------------------------
        // 5️⃣ Rotate token
        // ---------------------------------------
        stored.setRevokedAt(now);
        stored.setLastUsedAt(now);

        AuthResponse newTokens = issueTokens(user, userAgent, ipAddress);

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

        int updated = refreshTokenRepository.revokeOne(
                sessionId,
                userId,
                Instant.now()
        );

        if (updated == 0) {
            throw new IllegalArgumentException("Session not found");
        }
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(encoded);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash refresh token", e);
        }
    }

    private AuthResponse issueTokens(UserAccount user,
                                     String userAgent,
                                     String ipAddress) {

        JwtService.JwtToken access = jwtService.createAccessToken(user.getEmail());

        String refreshRaw = generateRefreshToken();

        persistRefreshToken(
                user.getId(),
                refreshRaw,
                userAgent,
                ipAddress
        );

        return new AuthResponse(
                access.token(),
                access.expiresAt(),
                user.getId(),
                user.getEmail(),
                refreshRaw
        );
    }

    private void persistRefreshToken(Long userId,
                                     String rawToken,
                                     String userAgent,
                                     String ipAddress) {

        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setTokenHash(TokenHash.sha256(rawToken));
        rt.setExpiresAt(Instant.now().plus(refreshDays, ChronoUnit.DAYS));
        rt.setLastUsedAt(Instant.now());
        rt.setUserAgent(userAgent);
        rt.setIpAddress(ipAddress);

        refreshTokenRepository.save(rt);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
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

    private String generateSlug(String email) {
        return email.split("@")[0]
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                + "-" + System.currentTimeMillis();
    }

    private void revokeAllUserSessions(Long userId, Instant now) {
        refreshTokenRepository
                .findByUserIdAndRevokedAtIsNull(userId)
                .forEach(token -> {
                    token.setRevokedAt(now);
                    refreshTokenRepository.save(token);
                });
    }
}