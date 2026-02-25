package uk.co.quietadmin.sharedlinks.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.domain.customer.CurrentUserService;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.sharedlinks.mapper.SharedLinkMapper;
import uk.co.quietadmin.sharedlinks.model.SharedLink;
import uk.co.quietadmin.sharedlinks.model.SharedLinkFolder;
import uk.co.quietadmin.sharedlinks.service.SharedLinksService;

// NOTE: Replace these with your existing auth helpers:
// - currentGroupId()
// - currentUserId()
// and your existing access control (owner/admin/editor etc).

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shared-links")
public class SharedLinksController {
    private final SharedLinksService service;
    private final CurrentUserService currentUserService;
    private final SharedLinkMapper mapper;

    @GetMapping
    public List<SharedLinkResponse> listActive(
            Principal principal
    ) {
        Long groupId = currentUserService.getCurrentGroupId(principal.getName());
        return service.listActiveLinks(groupId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    public SharedLinkResponse create(
            Principal principal,
            @RequestBody @Valid CreateSharedLinkRequest req
    ) {
        Long groupId = currentUserService.getCurrentGroupId(principal.getName());
        Membership membership = currentUserService.getMembership(principal.getName());

        Long userId = membership.getUserId();

        SharedLink created = service.createLink(groupId, userId, req.title(), req.url(), req.description(), req.folderId());
        return toResponse(created);
    }

    @PostMapping("/{id}/archive")
    public void archive(
            Principal principal,
            @PathVariable Long id
    ) {
        Long groupId = currentUserService.getCurrentGroupId(principal.getName());

        service.archiveLink(groupId, id);
    }

    @PostMapping("/{id}/unarchive")
    public void unarchive(
            Principal principal,
            @PathVariable Long id
    ) {
        Long groupId = currentUserService.getCurrentGroupId(principal.getName());

        service.unarchiveLink(groupId, id);
    }

    public SharedLinkResponse toResponse(SharedLink l) {
        return mapper.toResponse(l);
    }

    @GetMapping("/folders")
    public List<SharedLinkFolderResponse> listFolders(
            Principal principal
    ) {
        Long groupId = currentUserService.getCurrentGroupId(principal.getName());

        return service.listFolders(groupId)
                .stream()
                .map(this::toFolderResponse)
                .toList();
    }

    @PostMapping("/folders")
    public SharedLinkFolderResponse createFolder(
            Principal principal,
            @RequestBody @Valid CreateSharedLinkFolderRequest req
    ) {
        Membership membership = currentUserService.getMembership(principal.getName());
        requireEditorOrAbove(membership);

        Long groupId = membership.getGroupId();

        return toFolderResponse(
                service.createFolder(groupId, req.name())
        );
    }

    @PatchMapping("/folders/{id}")
    public SharedLinkFolderResponse renameFolder(
            Principal principal,
            @PathVariable Long id,
            @RequestBody @Valid RenameSharedLinkFolderRequest req
    ) {
        Membership membership = currentUserService.getMembership(principal.getName());
        requireEditorOrAbove(membership);

        Long groupId = membership.getGroupId();

        return toFolderResponse(
                service.renameFolder(groupId, id, req.name())
        );
    }

    @DeleteMapping("/folders/{id}")
    public void deleteFolder(
            Principal principal,
            @PathVariable Long id
    ) {
        Membership membership = currentUserService.getMembership(principal.getName());
        requireEditorOrAbove(membership);

        Long groupId = membership.getGroupId();

        service.deleteFolder(groupId, id);
    }

    @PostMapping("/folders/reorder")
    public void reorderFolders(
            Principal principal,
            @RequestBody @Valid ReorderSharedLinkFoldersRequest req
    ) {
        Membership membership = currentUserService.getMembership(principal.getName());
        requireEditorOrAbove(membership);

        Long groupId = membership.getGroupId();

        service.reorderFolders(groupId, req.folderIds());
    }

    @PostMapping("/reorder")
    public void reorderLinks(
            Principal principal,
            @RequestParam(required = false) Long folderId,
            @RequestBody ReorderSharedLinksRequest req
    ) {
        Membership membership = currentUserService.getMembership(principal.getName());
        requireEditorOrAbove(membership);

        service.reorderLinks(membership.getGroupId(), folderId, req.linkIds());
    }

    @GetMapping("/dashboard")
    public SharedLinksDashboardResponse dashboard(Principal principal) {
        Long groupId = currentUserService.getCurrentGroupId(principal.getName());
        return service.getDashboard(groupId);
    }

    private SharedLinkFolderResponse toFolderResponse(SharedLinkFolder f) {
        return new SharedLinkFolderResponse(
                f.getId(),
                f.getGroupId(),
                f.getName(),
                f.getSortOrder(),
                f.getCreatedAt(),
                f.getUpdatedAt()
        );
    }

    private void requireEditorOrAbove(Membership membership) {
        if (!membership.isAdmin()) {
            throw new IllegalStateException("Not permitted");
        }
    }
}