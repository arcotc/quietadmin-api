package uk.co.quietadmin.web.auth;

import java.time.Instant;

public record AuthResponse(

        // Auth state
        boolean authenticated,
        boolean verificationRequired,

        // Tokens
        String accessToken,
        String refreshToken,
        Instant expiresAt,

        // User identity
        Long userId,
        String email,
        String firstName,
        String lastName

) {

    /* ---------------------------
       Successful login response
    ---------------------------- */

    public static AuthResponse success(
            String accessToken,
            String refreshToken,
            Instant expiresAt,
            Long userId,
            String email,
            String firstName,
            String lastName
    ) {
        return new AuthResponse(
                true,
                false,
                accessToken,
                refreshToken,
                expiresAt,
                userId,
                email,
                firstName,
                lastName
        );
    }

    /* ---------------------------
       Email verification required
    ---------------------------- */

    public static AuthResponse verificationRequired(String email) {
        return new AuthResponse(
                false,
                true,
                null,
                null,
                null,
                null,
                email,
                null,
                null
        );
    }

    /* ---------------------------
       Strip refresh token (optional)
    ---------------------------- */

    public AuthResponse withoutRefreshToken() {
        return new AuthResponse(
                authenticated,
                verificationRequired,
                accessToken,
                null,
                expiresAt,
                userId,
                email,
                firstName,
                lastName
        );
    }
}
