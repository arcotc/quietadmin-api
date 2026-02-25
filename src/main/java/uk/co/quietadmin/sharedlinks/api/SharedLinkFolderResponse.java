package uk.co.quietadmin.sharedlinks.api;

import java.time.Instant;

public record SharedLinkFolderResponse(
        Long id,
        Long groupId,
        String name,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {}