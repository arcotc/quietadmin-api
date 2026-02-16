package uk.co.quietadmin.web.auth;

public record RegisterRequest(
        String groupName,
        String email,
        String password,
        String firstName,
        String lastName
) {}
