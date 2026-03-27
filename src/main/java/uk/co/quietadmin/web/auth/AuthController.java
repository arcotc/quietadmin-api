package uk.co.quietadmin.web.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.api.ratelimit.RateLimitService;
import uk.co.quietadmin.domain.auth.RefreshToken;
import uk.co.quietadmin.domain.auth.RefreshTokenRepository;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;
import uk.co.quietadmin.security.IpResolver;
import uk.co.quietadmin.security.TokenHash;
import uk.co.quietadmin.service.auth.AuthService;

import java.security.Principal;
import java.time.Duration;
import java.util.List;

import static uk.co.quietadmin.security.SecurityEventService.getIp;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {
    @Value("${security.cookie.secure:true}")
    private boolean secureCookies;

    @Value("${security.jwt.refresh-expiration-seconds}")
    private long refreshExpirationSeconds;

    private final RateLimitService rateLimitService;
    private final AuthService authService;
    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthController(
            AuthService authService,
            UserAccountRepository userAccountRepository,
            RefreshTokenRepository refreshTokenRepository,
            RateLimitService rateLimitService
    ) {
        this.authService = authService;
        this.userAccountRepository = userAccountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.rateLimitService = rateLimitService;
    }

    // ===============================
    // LOGIN
    // ===============================

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        log.debug("Received login request: {}", request.email());

        String ip = IpResolver.resolve(httpRequest);

//        try {
            AuthResponse auth = authService.login(
                    request.email(),
                    request.password(),
                    userAgent,
                    ip,
                    deviceId,
                    httpRequest
            );

            addRefreshCookie(response, auth.refreshToken());
            return ResponseEntity.ok(auth.withoutRefreshToken());
//        } catch (IllegalArgumentException illegalArgumentException) {
//            return ResponseEntity.badRequest().body(
//                    AuthResponse.failureWithMessage(illegalArgumentException.getMessage())
//            );
//        }
    }

    // ===============================
// SET PASSWORD
// ===============================

    @PostMapping("/set-password")
    public ResponseEntity<AuthResponse> setPassword(
            @RequestBody SetPasswordRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {

        String ip = IpResolver.resolve(httpRequest);

        AuthResponse auth = authService.setPassword(
                request.token(),
                request.password(),
                userAgent,
                ip,
                deviceId
        );

        addRefreshCookie(response, auth.refreshToken());
        return ResponseEntity.ok(auth.withoutRefreshToken());
    }

    // ===============================
    // REGISTER
    // ===============================

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest registerRequest,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        ipRateLimitCheck("register:ip:", request, 5, "Too many sign-up attempts. Please try again later.");

        // ✅ Email-based limit (prevents repeated spam to same address)
        String email = registerRequest.email().trim().toLowerCase();
        rateLimitCheck(getIp(request), "register:email:", email, 3, "This email has been used recently. Please check your inbox or try again later.");

        String ip = IpResolver.resolve(request);
        AuthResponse auth = authService.register(
                registerRequest.groupName(),
                email,
                registerRequest.firstName(),
                registerRequest.lastName(),
                userAgent,
                ip,
                deviceId
        );

        return ResponseEntity.ok(auth);
    }

    private void rateLimitCheck(String ip, String key, String data, int maxAttempts, String message) {
        rateLimitService.assertAllowed(
                ip,
                key + data,
                maxAttempts,
                Duration.ofHours(1),
                message
        );
    }


    private void ipRateLimitCheck(String key, HttpServletRequest request, int maxAttempts, String message) {
        String ip = getIp(request);

        rateLimitCheck(ip, key, ip, maxAttempts, message);
    }

    // ===============================
    // REFRESH
    // ===============================

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (refreshToken == null) {
            throw new IllegalArgumentException("Refresh token missing");
        }

        ipRateLimitCheck("refresh:ip:", request, 60, "Too many refresh attempts.");

        AuthResponse auth = authService.refresh(
            refreshToken,
            userAgent,
            getIp(request),
            deviceId
        );

        addRefreshCookie(response, auth.refreshToken());
        return ResponseEntity.ok(auth.withoutRefreshToken());
    }

    // ===============================
    // LOGOUT ALL
    // ===============================

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            HttpServletResponse response,
            Principal principal
    ) {

        if (principal == null) {
            throw new IllegalArgumentException("Not authenticated");
        }

        String email = principal.getName();

        UserAccount user = userAccountRepository
                .findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        authService.logoutAll(user.getId());
        clearRefreshCookie(response);

        return ResponseEntity.noContent().build();
    }

    // ===============================
    // SESSION LIST
    // ===============================

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> listSessions(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            Principal principal
    ) {

        if (principal == null) {
            throw new IllegalArgumentException("Not authenticated");
        }

        String email = principal.getName();

        UserAccount user = userAccountRepository
                .findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<SessionResponse> sessions =
                authService.listSessions(user.getId(), refreshToken);

        return ResponseEntity.ok(sessions);
    }

    // ===============================
    // REVOKE SINGLE SESSION
    // ===============================

    @PostMapping("/sessions/{id}/revoke")
    public ResponseEntity<Void> revokeSession(
            @PathVariable Long id,
            Principal principal,
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response
    ) {

        if (principal == null) {
            throw new IllegalArgumentException("Not authenticated");
        }

        String email = principal.getName();

        UserAccount user = userAccountRepository
                .findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        authService.revokeSession(user.getId(), id);

        // If revoking current device → clear cookie
        if (refreshToken != null) {
            String hash = TokenHash.sha256(refreshToken);

            RefreshToken current = refreshTokenRepository
                    .findByTokenHashAndRevokedAtIsNull(hash)
                    .orElse(null);

            if (current != null && current.getId().equals(id)) {
                clearRefreshCookie(response);
            }
        }

        return ResponseEntity.noContent().build();
    }

    // ===============================
    // EMAIL VERIFICATION
    // ===============================

    @GetMapping("/verify")
    public ResponseEntity<AuthResponse> verify(
            @RequestParam String token,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthResponse auth = authService.verifyEmail(
                token,
                userAgent,
                getIp(request),
                deviceId
        );

        if (auth.authenticated()) {
            addRefreshCookie(response, auth.refreshToken());
            return ResponseEntity.ok(auth.verifyRefreshToken());
        }

        return ResponseEntity.ok(auth);
    }

    @PostMapping("/activate")
    public ResponseEntity<AuthResponse> activate(
            @RequestBody ActivateRequest activateRequest,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthResponse auth = authService.activateAccount(
                activateRequest.email(),
                activateRequest.password(),
                userAgent,
                getIp(request),
                deviceId
        );

        addRefreshCookie(response, auth.refreshToken());
        return ResponseEntity.ok(auth.withoutRefreshToken());
    }

    // ===============================
// RESEND VERIFICATION
// ===============================

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(
            @RequestBody ResendVerificationRequest resendVerificationRequest,
            HttpServletRequest request
    ) {
        ipRateLimitCheck("resend-verification:ip:", request, 5, "Too many requests. Please try again later.");

        // ✅ Email-based limit (prevents repeated spam to same address)
        String email = resendVerificationRequest.email().trim().toLowerCase();
        rateLimitCheck(getIp(request), "resend-verification:email:", email, 3, "A verification email was sent recently. Please check your inbox.");

        authService.resendVerificationEmail(email);

        // Always return 204 to avoid email enumeration attacks
        return ResponseEntity.noContent().build();
    }

    // ===============================
    // COOKIE HELPERS
    // ===============================

    private void addRefreshCookie(HttpServletResponse response, String token) {

        ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(secureCookies)
                .path("/")
                .maxAge(refreshExpirationSeconds)
                .sameSite("Strict")
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {

        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(secureCookies)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }
}