package uk.co.quietadmin.sharedlinks.api;

import java.time.Instant;

public record SharedLinkResponse(
        Long id,
        Long groupId,
        Long folderId,
        String title,
        String url,
        String description,
        boolean pinned,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {}