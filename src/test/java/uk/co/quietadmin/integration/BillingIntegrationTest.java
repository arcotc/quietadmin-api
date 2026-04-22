package uk.co.quietadmin.integration;

import com.stripe.model.billingportal.Session;
import com.stripe.param.billingportal.SessionCreateParams;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import uk.co.quietadmin.domain.group.*;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;
import uk.co.quietadmin.domain.user.UserStatus;
import uk.co.quietadmin.security.JwtService;
import uk.co.quietadmin.service.mail.EmailService;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Billing endpoint sanity test.
 * Ensures the portal-session endpoint returns a URL and does not break silently.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingIntegrationTest {

    private static final String TEST_EMAIL = "billing-test@test.local";

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    @Autowired UserAccountRepository userRepo;
    @Autowired QaGroupRepository groupRepo;
    @Autowired MembershipRepository membershipRepo;

    @MockBean EmailService emailService;

    private Long userId;
    private Long groupId;

    @BeforeAll
    void seed() {
        QaGroup group = new QaGroup();
        group.setName("Billing Test Group");
        group.setSlug("billing-integration-test-group");
        group.setCreatedBy(0L);
        group.setPlanType(PlanType.STANDARD);
        group.setStripeSubscriptionStatus(StripeSubscriptionStatus.TRIALING);
        group.setStripeTrialEnd(Instant.now().plusSeconds(86400 * 30));
        group.setStripeCustomerId("cus_test_integration_123");
        group = groupRepo.save(group);
        groupId = group.getId();

        UserAccount user = new UserAccount();
        user.setEmail(TEST_EMAIL);
        user.setFirstName("Billing");
        user.setLastName("Test");
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
        jdbc.update("DELETE FROM refresh_token WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM membership WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_account WHERE id = ?", userId);
        jdbc.update("DELETE FROM qa_group WHERE id = ?", groupId);
    }

    /* ====================================================================
       billingPortalSession_returnsUrl
       ==================================================================== */

    @Test
    void billingPortalSession_returnsUrl() throws Exception {
        String accessToken = jwtService.createAccessToken(TEST_EMAIL).token();
        String portalUrl   = "https://billing.stripe.com/p/session/test_portal_abc123";

        try (MockedStatic<Session> stripeSession = mockStatic(Session.class)) {
            Session fakeSession = mock(Session.class);
            org.mockito.Mockito.when(fakeSession.getUrl()).thenReturn(portalUrl);
            stripeSession
                    .when(() -> Session.create(any(SessionCreateParams.class)))
                    .thenReturn(fakeSession);

            mockMvc.perform(get("/api/billing/portal-session")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").value(portalUrl));
        }
    }
}
