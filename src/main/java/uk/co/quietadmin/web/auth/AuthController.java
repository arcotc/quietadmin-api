package uk.co.quietadmin.web.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.service.auth.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @RequestBody RegisterRequest request) {

        UserAccount user = authService.register(
                request.email(),
                request.password()
        );

        return ResponseEntity.ok(
                new RegisterResponse(user.getId(), user.getEmail())
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(
                authService.login(request.email(), request.password())
        );
    }

    public record LoginResponse(String token) {}
}