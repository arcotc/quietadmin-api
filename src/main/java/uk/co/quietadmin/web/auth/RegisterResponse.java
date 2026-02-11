package uk.co.quietadmin.web.auth;

public record RegisterResponse(
        Long userId,
        String email
) {}