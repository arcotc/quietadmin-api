package uk.co.quietadmin.web.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.domain.auth.RefreshToken;
import uk.co.quietadmin.domain.auth.RefreshTokenRepository;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;
import uk.co.quietadmin.security.TokenHash;
import uk.co.quietadmin.service.auth.AuthService;

import java.security.Principal;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthController(AuthService authService, UserAccountRepository userAccountRepository, RefreshTokenRepository refreshTokenRepository) {
        this.authService = authService;
        this.userAccountRepository = userAccountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {

        if (refreshToken == null) throw new IllegalArgumentException("Refresh token missing");

        String ip = httpServletRequest.getRemoteAddr();

        AuthResponse auth = authService.refresh(refreshToken, userAgent, ip /* + deviceId if you add it */);

        addRefreshCookie(httpServletResponse, auth.refreshToken());
        return ResponseEntity.ok(auth.withoutRefreshToken());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {

        String ip = httpServletRequest.getRemoteAddr();

        AuthResponse auth = authService.login(request.email(), request.password(), userAgent, ip);

        addRefreshCookie(httpServletResponse, auth.refreshToken());

        return ResponseEntity.ok(auth.withoutRefreshToken());
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(HttpServletResponse response, Principal principal) {

        String email = principal.getName();
        UserAccount user = userAccountRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        authService.logoutAll(user.getId());

        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> listSessions(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            Principal principal) {

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

    @PostMapping("/sessions/{id}/revoke")
    public ResponseEntity<Void> revokeSession(
            @PathVariable Long id,
            Principal principal,
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {

        if (principal == null) {
            throw new IllegalArgumentException("Not authenticated");
        }

        String email = principal.getName();

        UserAccount user = userAccountRepository
                .findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        authService.revokeSession(user.getId(), id);

        // If user revoked the current device → clear cookie
        if (refreshToken != null) {
            String hash = TokenHash.sha256(refreshToken);
            RefreshToken current = refreshTokenRepository
                    .findByTokenHash(hash)
                    .orElse(null);

            if (current != null && current.getId().equals(id)) {
                clearRefreshCookie(response);
            }
        }

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        String ipAddress = httpRequest.getRemoteAddr();

        AuthResponse auth = authService.register(
                request.email(),
                request.password(),
                userAgent,
                ipAddress
        );

        addRefreshCookie(response, auth.refreshToken());

        return ResponseEntity.ok(auth.withoutRefreshToken());
    }

    private void addRefreshCookie(HttpServletResponse response, String token) {

        ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(true) // true in prod
                .path("/")
                .maxAge(Duration.ofDays(30))
                .sameSite("Strict")
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {

        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true) // true in production
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }
}