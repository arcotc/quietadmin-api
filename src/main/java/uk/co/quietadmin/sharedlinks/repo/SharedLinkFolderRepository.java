package uk.co.quietadmin.sharedlinks.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.quietadmin.sharedlinks.model.SharedLinkFolder;

import java.util.List;
import java.util.Optional;

public interface SharedLinkFolderRepository extends JpaRepository<SharedLinkFolder, Long> {
    List<SharedLinkFolder> findByGroupIdOrderBySortOrderAscNameAsc(Long groupId);
    Optional<SharedLinkFolder> findByIdAndGroupId(Long id, Long groupId);
}