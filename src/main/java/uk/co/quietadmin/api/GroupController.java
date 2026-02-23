package uk.co.quietadmin.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.domain.customer.CurrentUserService;
import uk.co.quietadmin.domain.group.GroupService;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.group.UpdateMemberRequest;
import uk.co.quietadmin.domain.group.dto.GroupDto;
import uk.co.quietadmin.domain.group.dto.MemberDto;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/group")
@RequiredArgsConstructor
public class GroupController {

    private final CurrentUserService currentUserService;
    private final GroupService groupService;

    @GetMapping
    public ResponseEntity<GroupDto> getGroup(Principal principal) {
        Membership membership = currentUserService.getMembership(principal.getName());
        return ResponseEntity.ok(groupService.getGroup(membership.getGroupId()));
    }

    @PutMapping
    public ResponseEntity<GroupDto> updateGroup(
            Principal principal,
            @RequestBody UpdateGroupRequest request
    ) {
        currentUserService.requireAdmin(principal.getName());
        Membership membership = currentUserService.getMembership(principal.getName());

        return ResponseEntity.ok(
                groupService.updateGroup(
                        membership.getGroupId(),
                        request.name(),
                        request.description()
                )
        );
    }

    @GetMapping("/members")
    public ResponseEntity<List<MemberDto>> members(Principal principal) {

        // Any authenticated group member can view
        Membership membership = currentUserService.getMembership(principal.getName());

        return ResponseEntity.ok(
                groupService.getMembers(membership.getGroupId())
        );
    }

    @PutMapping("/members/{userId}")
    public ResponseEntity<Void> updateMember(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateMemberRequest request,
            Principal principal
    ) {
        groupService.updateMember(userId, request, principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/members/{userId}/promote")
    public ResponseEntity<Void> promote(Principal principal, @PathVariable Long userId) {
        currentUserService.requireAdmin(principal.getName());
        Membership membership = currentUserService.getMembership(principal.getName());

        groupService.promoteToAdmin(membership.getGroupId(), userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/members/{userId}")
    public ResponseEntity<Void> remove(
            Principal principal,
            @PathVariable Long userId
    ) {
        currentUserService.requireAdmin(principal.getName());
        Membership membership = currentUserService.getMembership(principal.getName());

        groupService.removeMember(
                membership.getGroupId(),
                userId,
                membership.getUserId()
        );

        return ResponseEntity.noContent().build();
    }

    public record UpdateGroupRequest(String name, String description) {}
}