package uk.co.quietadmin.sharedlinks.api;

import java.util.List;

public record SharedLinksDashboardResponse(
        List<SharedLinkResponse> pinned,
        List<FolderWithLinks> folders,
        List<SharedLinkResponse> unsorted
) {}
