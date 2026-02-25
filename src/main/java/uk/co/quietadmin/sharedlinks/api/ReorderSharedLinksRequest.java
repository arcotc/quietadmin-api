package uk.co.quietadmin.sharedlinks.api;

import java.util.List;

public record ReorderSharedLinksRequest(
        List<Long> linkIds
) {}