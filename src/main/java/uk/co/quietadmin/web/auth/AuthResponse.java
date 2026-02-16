package uk.co.quietadmin.web.auth;

import java.time.Instant;

public record AuthResponse(

        // Auth state flags
        boolean authenticated,
        boolean verificationRequired,
        boolean checkoutRequired,

        // Checkout redirect (for Stripe flow)
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

    /* =========================================================
       Successful login / token issuance
       ========================================================= */

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
                true,   // authenticated
                false,  // verificationRequired
                false,  // checkoutRequired
                null,   // checkoutUrl
                accessToken,
                refreshToken,
                expiresAt,
                userId,
                email,
                firstName,
                lastName
        );
    }

    /* =========================================================
       Email verification required
       ========================================================= */

    public static AuthResponse verificationRequired(String email) {
        return new AuthResponse(
                false,  // authenticated
                true,   // verificationRequired
                false,  // checkoutRequired
                null,   // checkoutUrl
                null,
                null,
                null,
                null,
                email,
                null,
                null
        );
    }

    /* =========================================================
       Stripe Checkout required (NEW)
       Used by AuthService.register()
       ========================================================= */

    public static AuthResponse checkoutRedirect(
            String checkoutUrl,
            String email
    ) {
        return new AuthResponse(
                false,  // authenticated
                false,  // verificationRequired
                true,   // checkoutRequired
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

    /* =========================================================
       Strip refresh token (optional)
       Useful when returning user info but not rotating token
       ========================================================= */

    public AuthResponse withoutRefreshToken() {
        return new AuthResponse(
                authenticated,
                verificationRequired,
                checkoutRequired,
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
