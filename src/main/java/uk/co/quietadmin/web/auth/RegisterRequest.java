package uk.co.quietadmin.web.auth;

public record RegisterRequest(
        String email,
        String password,
        String firstName,
        String lastName
) {}
