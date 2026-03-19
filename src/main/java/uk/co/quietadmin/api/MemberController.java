package uk.co.quietadmin.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.domain.customer.CurrentUserService;
import uk.co.quietadmin.domain.group.MemberService;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.group.dto.MemberDto;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final CurrentUserService currentUserService;
    private final MemberService memberService;

    @PostMapping("/invite")
    public ResponseEntity<Void> invite(
            Principal principal,
            @RequestBody InviteRequest request
    ) {
        currentUserService.requireAdmin(principal.getName());
        Membership membership = currentUserService.getMembership(principal.getName());

        memberService.inviteMember(
                membership.getGroupId(),
                membership.getUserId(),
                request.firstName(),
                request.lastName(),
                request.email()
        );

        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<MemberDto>> get(Principal principal) {
//        currentUserService.requireAdmin(principal.getName());
        Membership membership = currentUserService.getMembership(principal.getName());

        List<MemberDto> members = memberService.findMembers(membership.getId());

        return ResponseEntity.ok(members);
    }

    public record InviteRequest(String firstName, String lastName, String email) {}
}