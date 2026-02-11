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

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

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

    @Transactional(readOnly = true)
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
    public AuthResponse refresh(String rawRefreshToken) {

        String hash = hash(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository
                .findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(hash, Instant.now())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (stored.isRevoked() || stored.isExpired()) {
            throw new IllegalArgumentException("Refresh token expired or revoked");
        }

        if (stored.getReplacedByTokenHash() != null) {
            // possible token reuse attack
            throw new IllegalArgumentException("Refresh token already used");
        }

        UserAccount user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Rotation: revoke old token and issue a new one
        stored.setRevokedAt(Instant.now());
        stored.setLastUsedAt(Instant.now());

        // issue new tokens
        AuthResponse response = issueTokens(user);

        // store linkage
        stored.setReplacedByTokenHash(TokenHash.sha256(response.refreshToken()));
        refreshTokenRepository.save(stored);

        return response;
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