package uk.co.quietadmin.api.billing;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCheckoutSessionRequest(
        @NotBlank @Email
        String email,
        @NotBlank @Size(min = 1, max = 100)
        String firstName,
        @NotBlank @Size(min = 2, max = 100)
        String groupName,
        @NotBlank @Size(min = 8, max = 100)
        String password
) {}
