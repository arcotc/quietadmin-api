package uk.co.quietadmin.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import uk.co.quietadmin.domain.auth.RefreshToken;
import uk.co.quietadmin.domain.auth.RefreshTokenRepository;
import uk.co.quietadmin.domain.group.*;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;
import uk.co.quietadmin.domain.user.UserStatus;
import uk.co.quietadmin.security.JwtService;
import uk.co.quietadmin.security.TokenHash;
import uk.co.quietadmin.service.mail.EmailService;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auth / session lifecycle integration tests.
 *
 * Verifies that expired tokens are rejected, valid refresh tokens
 * produce new access tokens, and logout properly revokes sessions.
 *
 * Uses PER_CLASS lifecycle so the test user is seeded once and
 * cleaned up once — avoids unique-constraint races between @BeforeEach calls.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthSessionTest {

    private static final String TEST_EMAIL = "auth-session-test@test.local";
    private static final String DEVICE_ID  = "test-device";
    private static final String USER_AGENT = "test-agent";
    private static final String IP         = "127.0.0.1";

    // Must match src/test/resources/application.properties
    private static final String JWT_SECRET =
            "test-secret-key-for-unit-tests-must-be-at-least-32-chars-long";

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    @Autowired UserAccountRepository userRepo;
    @Autowired QaGroupRepository groupRepo;
    @Autowired MembershipRepository membershipRepo;
    @Autowired RefreshTokenRepository refreshTokenRepo;

    @MockBean EmailService emailService;

    private Long userId;
    private Long groupId;

    @BeforeAll
    void seed() {
        QaGroup group = new QaGroup();
        group.setName("Auth Session Test Group");
        group.setSlug("auth-session-test-group");
        group.setCreatedBy(0L);
        group.setPlanType(PlanType.STANDARD);
        group.setStripeSubscriptionStatus(StripeSubscriptionStatus.TRIALING);
        group.setStripeTrialEnd(Instant.now().plusSeconds(86400 * 30));
        group = groupRepo.save(group);
        groupId = group.getId();

        UserAccount user = new UserAccount();
        user.setEmail(TEST_EMAIL);
        user.setFirstName("Auth");
        user.setLastName("Session");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setUserStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user = userRepo.save(user);
        userId = user.getId();

        group.setCreatedBy(userId);
        groupRepo.save(group);

        Membership membership = new Membership();
        membership.setUserId(userId);
        membership.setGroupId(groupId);
        membership.setRole(MembershipRole.ADMIN);
        membershipRepo.save(membership);
    }

    @AfterAll
    void cleanup() {
        // Delete via JDBC to bypass FK ordering issues in H2
        jdbc.update("DELETE FROM refresh_token WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM membership WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_account WHERE id = ?", userId);
        jdbc.update("DELETE FROM qa_group WHERE id = ?", groupId);
    }

    /* ====================================================================
       TEST 1 — Expired access token returns 401
       ==================================================================== */

    @Test
    void expiredAccessToken_returns401() throws Exception {
        String expiredToken = buildExpiredJwt(TEST_EMAIL);

        mockMvc.perform(get("/api/teams")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    /* ====================================================================
       TEST 2 — Valid refresh token produces a new access token
       ==================================================================== */

    @Test
    void refreshToken_allowsNewAccessToken() throws Exception {
        String refreshRaw = seedRefreshToken();

        try {
            mockMvc.perform(post("/api/auth/refresh")
                            .header("X-Device-Id", DEVICE_ID)
                            .header("User-Agent", USER_AGENT)
                            .cookie(new Cookie("refresh_token", refreshRaw)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authenticated").value(true))
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());
        } finally {
            jdbc.update("DELETE FROM refresh_token WHERE user_id = ?", userId);
        }
    }

    /* ====================================================================
       TEST 3 — Logout revokes the refresh token
       ==================================================================== */

    @Test
    void logout_invalidatesSession() throws Exception {
        String accessToken = jwtService.createAccessToken(TEST_EMAIL).token();
        String refreshRaw  = seedRefreshToken();

        // Logout — must return 204
        mockMvc.perform(post("/api/auth/logout-all")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // Refresh with the now-revoked token must fail
        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Device-Id", DEVICE_ID)
                        .header("User-Agent", USER_AGENT)
                        .cookie(new Cookie("refresh_token", refreshRaw)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void failedLogin_doesNotIssueRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "auth-session-test@test.local",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void verifyInvitedUserWithoutPassword_doesNotIssueTokens() throws Exception {
        String rawToken = "verify-no-password-token-" + System.nanoTime();
        String email = "verify-no-password-" + System.nanoTime() + "@test.local";

        UserAccount invited = new UserAccount();
        invited.setEmail(email);
        invited.setFirstName("Verify");
        invited.setLastName("NoPassword");
        invited.setPasswordHash(null);
        invited.setUserStatus(UserStatus.INVITED);
        invited.setEmailVerified(false);
        invited.setEmailVerificationToken(TokenHash.sha256(rawToken));
        invited.setEmailVerificationExpiresAt(Instant.now().plusSeconds(3600));
        invited = userRepo.save(invited);

        try {
            mockMvc.perform(get("/api/auth/verify").param("token", rawToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authenticated").value(false))
                    .andExpect(jsonPath("$.passwordSetupRequired").value(true))
                    .andExpect(jsonPath("$.accessToken").value(nullValue()))
                    .andExpect(jsonPath("$.refreshToken").value(nullValue()))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        } finally {
            jdbc.update("DELETE FROM user_account WHERE id = ?", invited.getId());
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private String buildExpiredJwt(String email) {
        SecretKey key = Keys.hmacShaKeyFor(
                JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(7200);
        return Jwts.builder()
                .issuer("quietadmin-test")
                .audience().add("quietadmin-app").and()
                .subject(email)
                .issuedAt(Date.from(past.minusSeconds(600)))
                .expiration(Date.from(past))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Persists a refresh token whose fingerprint matches the test request.
     * MockMvc resolves the remote address to 127.0.0.1.
     */
    private String seedRefreshToken() {
        String raw         = "test-raw-refresh-" + System.currentTimeMillis();
        String fingerprint = TokenHash.sha256(DEVICE_ID + "|" + USER_AGENT + "|" + IP);

        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setTokenHash(TokenHash.sha256(raw));
        rt.setExpiresAt(Instant.now().plusSeconds(86400));
        rt.setLastUsedAt(Instant.now());
        rt.setUserAgent(USER_AGENT);
        rt.setIpAddress(IP);
        rt.setDeviceId(DEVICE_ID);
        rt.setFingerprintHash(fingerprint);
        refreshTokenRepo.save(rt);

        return raw;
    }
}
