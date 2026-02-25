package uk.co.quietadmin.sharedlinks.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.co.quietadmin.sharedlinks.model.SharedLink;

import java.util.List;
import java.util.Optional;

public interface SharedLinkRepository extends JpaRepository<SharedLink, Long> {

    List<SharedLink> findByGroupIdAndArchivedAtIsNullOrderByPinnedDescFolderIdAscSortOrderAscTitleAsc(Long groupId);

    List<SharedLink> findByGroupIdAndArchivedAtIsNotNullOrderByArchivedAtDescTitleAsc(Long groupId);

    Optional<SharedLink> findByIdAndGroupId(Long id, Long groupId);

    List<SharedLink> findByGroupIdAndArchivedAtIsNullAndFolderIsNullOrderBySortOrderAsc(Long groupId);

    List<SharedLink> findByGroupIdAndArchivedAtIsNullAndFolder_IdOrderBySortOrderAsc(Long groupId, Long folderId);

    @Query("""
            SELECT l
            FROM SharedLink l
            LEFT JOIN l.folder f
            WHERE l.groupId = :groupId
            AND l.archivedAt IS NULL
            ORDER BY 
                l.pinned DESC,
                f.sortOrder ASC NULLS FIRST,
                l.sortOrder ASC,
                l.title ASC
            """)
    List<SharedLink> findAllActiveOrdered(@Param("groupId") Long groupId);
}