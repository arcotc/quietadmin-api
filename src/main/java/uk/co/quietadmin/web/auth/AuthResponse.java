package uk.co.quietadmin.web.auth;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        Instant expiresAt,
        Long userId,
        String email
) {}