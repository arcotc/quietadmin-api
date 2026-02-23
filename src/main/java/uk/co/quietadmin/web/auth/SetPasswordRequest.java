package uk.co.quietadmin.web.auth;

public record SetPasswordRequest(
        String token,
        String password
) {}