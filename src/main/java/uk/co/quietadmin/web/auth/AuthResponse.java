package uk.co.quietadmin.web.auth;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        Instant expiresAt,
        Long userId,
        String email,
        String refreshToken
) {
    public AuthResponse withoutRefreshToken() {
        return new AuthResponse(
                accessToken,
                expiresAt,
                userId,
                email,
                null
        );
    }

    public static AuthResponse verificationRequired(String email) {
        return new AuthResponse(
                null,
                null,
                null,
                email,
                null
        );
    }
}