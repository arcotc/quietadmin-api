package uk.co.quietadmin.web.auth;

import java.time.Instant;

public record SessionResponse(
        Long id,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        String userAgent,
        String ipAddress,
        boolean current
) {
}