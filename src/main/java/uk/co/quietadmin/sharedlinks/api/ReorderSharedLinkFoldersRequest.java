package uk.co.quietadmin.sharedlinks.api;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReorderSharedLinkFoldersRequest(
        @NotEmpty
        List<Long> folderIds
) {}