package uk.co.quietadmin.sharedlinks.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSharedLinkRequest(
        @NotBlank @Size(max = 180) String title,
        @NotBlank @Size(max = 2048) String url,
        @Size(max = 500) String description,
        Long folderId
) {}