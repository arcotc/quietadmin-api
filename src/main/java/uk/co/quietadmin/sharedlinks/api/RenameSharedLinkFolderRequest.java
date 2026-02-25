package uk.co.quietadmin.sharedlinks.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameSharedLinkFolderRequest(
        @NotBlank
        @Size(max = 120)
        String name
) {}