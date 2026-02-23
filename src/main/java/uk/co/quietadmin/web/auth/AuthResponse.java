package uk.co.quietadmin.web.auth;

import java.time.Instant;

public record AuthResponse(

        // Auth state flags
        boolean authenticated,
        boolean verificationRequired,
        boolean checkoutRequired,
        boolean passwordSetupRequired,

        // Checkout redirect
        String checkoutUrl,

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

    /* =========================
       Successful login
       ========================= */

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
                false,
                false,
                null,
                accessToken,
                refreshToken,
                expiresAt,
                userId,
                email,
                firstName,
                lastName
        );
    }

    /* =========================
       Email verification required
       ========================= */

    public static AuthResponse verificationRequired(String email) {
        return new AuthResponse(
                false,
                true,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                email,
                null,
                null
        );
    }

    /* =========================
       Stripe checkout required
       ========================= */

    public static AuthResponse checkoutRedirect(
            String checkoutUrl,
            String email
    ) {
        return new AuthResponse(
                false,
                false,
                true,
                false,
                checkoutUrl,
                null,
                null,
                null,
                null,
                email,
                null,
                null
        );
    }

    /* =========================
       Password setup required
       ========================= */

    public static AuthResponse passwordSetupRequired(String email) {
        return new AuthResponse(
                false,
                false,
                false,
                true,
                null,
                null,
                null,
                null,
                null,
                email,
                null,
                null
        );
    }

    /* =========================
       Strip refresh token
       ========================= */

    public AuthResponse withoutRefreshToken() {
        return new AuthResponse(
                authenticated,
                verificationRequired,
                checkoutRequired,
                passwordSetupRequired,
                checkoutUrl,
                accessToken,
                null,
                expiresAt,
                userId,
                email,
                firstName,
                lastName
        );
    }

    /* =========================
       Strip refresh token
       ========================= */

    public AuthResponse verifyRefreshToken() {
        return new AuthResponse(
                authenticated,
                verificationRequired,
                checkoutRequired,
                true,
                checkoutUrl,
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