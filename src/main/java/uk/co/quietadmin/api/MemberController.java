package uk.co.quietadmin.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.api.ratelimit.RateLimitService;
import uk.co.quietadmin.domain.customer.CurrentUserService;
import uk.co.quietadmin.domain.group.MemberService;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.group.dto.MemberDto;
import uk.co.quietadmin.security.SecurityEventService;

import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static uk.co.quietadmin.security.SecurityEventService.getIp;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {
    private final RateLimitService rateLimitService;
    private final CurrentUserService currentUserService;
    private final MemberService memberService;
    private final SecurityEventService securityEventService;

    @PostMapping("/invite")
    public ResponseEntity<Void> invite(
            HttpServletRequest request,
            Principal principal,
            @RequestBody InviteRequest inviteRequest
    ) {
        currentUserService.requireAdmin(principal.getName());
        Membership membership = currentUserService.getMembership(principal.getName());

        rateLimitService.assertAllowed(
                getIp(request),
                "invite:" + membership.getGroupId() + ":" + membership.getUserId(),
                10,
                Duration.ofHours(1),
                "You've sent quite a few invitations. Please wait a little while before sending more."
        );

        memberService.inviteMember(
                membership.getGroupId(),
                membership.getUserId(),
                inviteRequest.firstName(),
                inviteRequest.lastName(),
                inviteRequest.email()
        );

        securityEventService.audit(
                "MEMBER_INVITED",
                membership.getUserId(),
                membership.getGroupId(),
                Map.of("email", inviteRequest.email().trim().toLowerCase())
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