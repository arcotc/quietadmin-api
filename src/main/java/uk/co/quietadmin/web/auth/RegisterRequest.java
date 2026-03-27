package uk.co.quietadmin.web.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String groupName,
        @NotBlank String email,
        @Size(min = 6, max = 100) String password,
        @Size(min = 1, max = 100) String firstName,
        @Size(min = 2, max = 100) String lastName
) {}
