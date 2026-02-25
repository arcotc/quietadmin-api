package uk.co.quietadmin.sharedlinks.api;

import java.util.List;

public record FolderWithLinks(
        Long id,
        String name,
        List<SharedLinkResponse> links
) {}