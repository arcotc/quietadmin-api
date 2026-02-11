package uk.co.quietadmin.web.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.service.auth.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {

        if (refreshToken == null) {
            throw new IllegalArgumentException("Refresh token missing");
        }

        AuthResponse auth = authService.refresh(refreshToken);

        // rotate refresh cookie
        addRefreshCookie(response, auth.refreshToken());

        return ResponseEntity.ok(auth.withoutRefreshToken());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response) {

        AuthResponse auth = authService.login(request.email(), request.password());

        addRefreshCookie(response, auth.refreshToken());

        return ResponseEntity.ok(auth.withoutRefreshToken());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest request,
            HttpServletResponse response) {

        AuthResponse auth = authService.register(request.email(), request.password());

        addRefreshCookie(response, auth.refreshToken());

        return ResponseEntity.ok(auth.withoutRefreshToken());
    }

    private void addRefreshCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refresh_token", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // true in prod
        cookie.setPath("/api/auth");
        cookie.setMaxAge(60 * 60 * 24 * 30); // 30 days
        response.addCookie(cookie);
    }
}