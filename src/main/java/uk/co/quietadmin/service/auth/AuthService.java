package uk.co.quietadmin.service.auth;

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

    private final UserAccountRepository userRepository;
    private final QaGroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(String email, String rawPassword) {

        String normalizedEmail = normalizeEmail(email);

        userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .ifPresent(u -> { throw new IllegalArgumentException("Email already registered"); });

        // 1) Create user
        UserAccount user = new UserAccount();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);

        // 2) Create group
        QaGroup group = new QaGroup();
        group.setName(defaultGroupName(normalizedEmail));
        group.setSlug(generateSlug(normalizedEmail));
        group.setCreatedBy(user.getId());
        group.setTrialEndsAt(Instant.now().plus(trialDays, ChronoUnit.DAYS));
        group = groupRepository.save(group);

        // 3) Membership as ADMIN
        Membership membership = new Membership();
        membership.setUserId(user.getId());
        membership.setGroupId(group.getId());
        membership.setRole("ADMIN");
        membershipRepository.save(membership);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(String email, String rawPassword) {

        String normalizedEmail = normalizeEmail(email);

        UserAccount user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not active");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, String userAgent, String ipAddress) {

        String hash = TokenHash.sha256(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        // 🔥 Replay detection:
        // if revoked OR already rotated -> token reuse attempt
        boolean replayAttempt =
                stored.getRevokedAt() != null || stored.getReplacedByTokenHash() != null;

        if (replayAttempt) {
            // Lockout: revoke every active refresh token for this user
            refreshTokenRepository.revokeAllForUser(stored.getUserId(), Instant.now());

            // Optional: escalate account status
            // userRepository.findById(stored.getUserId()).ifPresent(u -> { u.setStatus(UserStatus.SUSPENDED); userRepository.save(u); });

            throw new IllegalArgumentException("Refresh token reuse detected. Signed out everywhere.");
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token expired");
        }

        UserAccount user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not active");
        }

        // rotate: revoke old
        stored.setRevokedAt(Instant.now());
        stored.setLastUsedAt(Instant.now());
        stored.setUserAgent(userAgent);
        stored.setIpAddress(ipAddress);

        AuthResponse next = issueTokens(user, userAgent, ipAddress);

        stored.setReplacedByTokenHash(TokenHash.sha256(next.refreshToken()));
        refreshTokenRepository.save(stored);

        return next;
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

    private AuthResponse issueTokens(UserAccount user) {
        JwtService.JwtToken access = jwtService.createAccessToken(user.getEmail());
        String refreshRaw = generateRefreshToken();
        persistRefreshToken(user.getId(), refreshRaw);

        return new AuthResponse(
                access.token(),
                access.expiresAt(),
                user.getId(),
                user.getEmail(),
                refreshRaw
        );
    }

    private AuthResponse issueTokens(UserAccount user, String userAgent, String ipAddress) {
        JwtService.JwtToken access = jwtService.createAccessToken(user.getEmail());

        String refreshRaw = generateRefreshToken();
        persistRefreshToken(user.getId(), refreshRaw, userAgent, ipAddress /*, deviceId*/);

        return new AuthResponse(
                access.token(),
                access.expiresAt(),
                user.getId(),
                user.getEmail(),
                refreshRaw
        );
    }

    private void persistRefreshToken(Long userId, String rawToken, String userAgent, String ipAddress) {
        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setTokenHash(TokenHash.sha256(rawToken));
        rt.setExpiresAt(Instant.now().plus(refreshDays, ChronoUnit.DAYS));
        rt.setUserAgent(userAgent);
        rt.setIpAddress(ipAddress);
        refreshTokenRepository.save(rt);
    }

    private void persistRefreshToken(Long userId, String rawToken) {
        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setTokenHash(TokenHash.sha256(rawToken));
        rt.setExpiresAt(Instant.now().plus(refreshDays, ChronoUnit.DAYS));
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
}