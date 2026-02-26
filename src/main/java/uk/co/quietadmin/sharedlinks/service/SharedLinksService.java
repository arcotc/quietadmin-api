package uk.co.quietadmin.sharedlinks.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.quietadmin.sharedlinks.api.FolderWithLinks;
import uk.co.quietadmin.sharedlinks.api.SharedLinkResponse;
import uk.co.quietadmin.sharedlinks.api.SharedLinksController;
import uk.co.quietadmin.sharedlinks.api.SharedLinksDashboardResponse;
import uk.co.quietadmin.sharedlinks.mapper.SharedLinkMapper;
import uk.co.quietadmin.sharedlinks.model.SharedLink;
import uk.co.quietadmin.sharedlinks.model.SharedLinkFolder;
import uk.co.quietadmin.sharedlinks.repo.SharedLinkFolderRepository;
import uk.co.quietadmin.sharedlinks.repo.SharedLinkRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SharedLinksService {

    private final SharedLinkRepository linkRepo;
    private final SharedLinkFolderRepository folderRepo;
    private final SharedLinkMapper mapper;

    public List<SharedLinkFolder> listFolders(Long groupId) {
        return folderRepo.findByGroupIdOrderBySortOrderAscNameAsc(groupId);
    }

    public List<SharedLink> listActiveLinks(Long groupId) {
        return linkRepo.findAllActiveOrdered(groupId);
    }

    @Transactional
    public SharedLink createLink(Long groupId, Long actorUserId, String title, String url, String description, Long folderId) {
        SharedLinkFolder folder = null;
        if (folderId != null) {
            folder = folderRepo.findByIdAndGroupId(folderId, groupId)
                    .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        }

        // (Optional) basic normalization:
        String trimmedUrl = url.trim();
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            trimmedUrl = "https://" + trimmedUrl;
        }

        SharedLink link = SharedLink.builder()
                .groupId(groupId)
                .folder(folder)
                .title(title.trim())
                .url(trimmedUrl)
                .description(description == null ? null : description.trim())
                .createdBy(actorUserId)
                .build();

        return linkRepo.save(link);
    }

    @Transactional
    public void archiveLink(Long groupId, Long linkId) {
        SharedLink link = linkRepo.findByIdAndGroupId(linkId, groupId)
                .orElseThrow(() -> new IllegalArgumentException("Link not found"));
        link.setArchivedAt(Instant.now());
    }

    @Transactional
    public void unarchiveLink(Long groupId, Long linkId) {
        SharedLink link = linkRepo.findByIdAndGroupId(linkId, groupId)
                .orElseThrow(() -> new IllegalArgumentException("Link not found"));
        link.setArchivedAt(null);
    }

    @Transactional
    public SharedLinkFolder createFolder(Long groupId, String name) {

        int nextSort = folderRepo.findByGroupIdOrderBySortOrderAscNameAsc(groupId)
                .stream()
                .mapToInt(SharedLinkFolder::getSortOrder)
                .max()
                .orElse(-1) + 1;

        SharedLinkFolder folder = SharedLinkFolder.builder()
                .groupId(groupId)
                .name(name.trim())
                .sortOrder(nextSort)
                .build();

        return folderRepo.save(folder);
    }

    @Transactional
    public SharedLinkFolder renameFolder(Long groupId, Long folderId, String name) {
        SharedLinkFolder folder = folderRepo.findByIdAndGroupId(folderId, groupId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));

        folder.setName(name.trim());
        return folder;
    }

    @Transactional
    public void deleteFolder(Long groupId, Long folderId) {
        SharedLinkFolder folder = folderRepo.findByIdAndGroupId(folderId, groupId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));

        folderRepo.delete(folder);
    }

    @Transactional
    public void reorderFolders(Long groupId, List<Long> orderedIds) {

        List<SharedLinkFolder> folders = folderRepo.findByGroupIdOrderBySortOrderAscNameAsc(groupId);

        if (folders.size() != orderedIds.size()) {
            throw new IllegalArgumentException("Invalid folder list");
        }

        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);

            SharedLinkFolder folder = folders.stream()
                    .filter(f -> f.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Folder mismatch"));

            folder.setSortOrder(i);
        }
    }

    @Transactional
    public void reorderLinks(Long groupId, Long folderId, List<Long> orderedIds) {

        List<SharedLink> links;

        if (folderId == null) {
            links = linkRepo.findByGroupIdAndArchivedAtIsNullAndFolderIsNullOrderBySortOrderAsc(groupId);
        } else {
            links = linkRepo.findByGroupIdAndArchivedAtIsNullAndFolder_IdOrderBySortOrderAsc(groupId, folderId);
        }

        if (links.size() != orderedIds.size()) {
            throw new IllegalArgumentException("Invalid link list");
        }

        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);

            SharedLink link = links.stream()
                    .filter(l -> l.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Link mismatch"));

            link.setSortOrder(i);
        }
    }

    public SharedLinksDashboardResponse getDashboard(Long groupId) {

        List<SharedLink> all = listActiveLinks(groupId);
        List<SharedLinkFolder> folders = listFolders(groupId);

        List<SharedLinkResponse> pinned = new ArrayList<>();
        List<SharedLinkResponse> unsorted = new ArrayList<>();
        Map<Long, List<SharedLinkResponse>> folderMap = new LinkedHashMap<>();

        for (SharedLinkFolder folder : folders) {
            folderMap.put(folder.getId(), new ArrayList<>());
        }

        for (SharedLink link : all) {

            SharedLinkResponse dto = mapper.toResponse(link);

            if (link.isPinned()) {
                pinned.add(dto);
                continue;
            }

            if (link.getFolder() == null) {
                unsorted.add(dto);
            } else {
                folderMap.get(link.getFolder().getId()).add(dto);
            }
        }

        List<FolderWithLinks> folderDtos = folders.stream()
                .map(f -> new FolderWithLinks(
                        f.getId(),
                        f.getName(),
                        folderMap.get(f.getId())
                ))
                .toList();

        return new SharedLinksDashboardResponse(
                pinned,
                folderDtos,
                unsorted
        );
    }

    @Transactional
    public void setPinned(Long groupId, Long linkId, boolean pinned) {
        SharedLink link = linkRepo.findByIdAndGroupId(linkId, groupId)
                .orElseThrow(() -> new IllegalArgumentException("Link not found"));

        link.setPinned(pinned);
    }
}