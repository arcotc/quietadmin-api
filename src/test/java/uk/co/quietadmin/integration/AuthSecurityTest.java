package uk.co.quietadmin.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.co.quietadmin.service.mail.EmailService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that protected API endpoints return 401 without a valid JWT.
 * Uses a full Spring Boot context backed by H2 in-memory database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityTest {

    @Autowired
    MockMvc mockMvc;

    // EmailService makes real SMTP calls — replace with no-op in tests
    @MockBean
    EmailService emailService;

    @Test
    void protectedEndpoint_withNoToken_returns401() throws Exception {
        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/rotas")
                        .header("Authorization", "Bearer this.is.not.a.valid.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withMalformedBearer_returns401() throws Exception {
        mockMvc.perform(get("/api/members")
                        .header("Authorization", "NotBearer abc123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpoint_login_isAccessibleWithoutToken() throws Exception {
        // Login endpoint is public — a GET returns 405 (wrong method) but NOT 401
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }
}
