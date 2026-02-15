package uk.co.quietadmin.api.billing;

public record CreateCheckoutSessionRequest(
        String email,
        String firstName,
        String groupName,
        String password
) {}