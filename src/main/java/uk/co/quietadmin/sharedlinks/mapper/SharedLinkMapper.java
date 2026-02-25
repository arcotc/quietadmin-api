package uk.co.quietadmin.sharedlinks.mapper;

import org.springframework.stereotype.Component;
import uk.co.quietadmin.sharedlinks.api.SharedLinkResponse;
import uk.co.quietadmin.sharedlinks.model.SharedLink;

@Component
public class SharedLinkMapper {

    public SharedLinkResponse toResponse(SharedLink l) {
        return new SharedLinkResponse(
                l.getId(),
                l.getGroupId(),
                l.getFolder() == null ? null : l.getFolder().getId(),
                l.getTitle(),
                l.getUrl(),
                l.getDescription(),
                l.isPinned(),
                l.getArchivedAt(),
                l.getCreatedAt(),
                l.getUpdatedAt()
        );
    }
}