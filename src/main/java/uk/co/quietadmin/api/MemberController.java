package uk.co.quietadmin.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.api.ratelimit.RateLimitService;
import uk.co.quietadmin.domain.customer.CurrentUserService;
import uk.co.quietadmin.domain.group.MemberService;
import uk.co.quietadmin.domain.group.MemberService.InvitationEmailPayload;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.group.dto.MemberDto;
import uk.co.quietadmin.security.SecurityEventService;
import uk.co.quietadmin.service.mail.EmailService;

import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static uk.co.quietadmin.security.SecurityEventService.getIp;

@Slf4j
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {
    private final RateLimitService rateLimitService;
    private final CurrentUserService currentUserService;
    private final MemberService memberService;
    private final EmailService emailService;
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

        // Transaction commits on return — email sent after commit
        Optional<InvitationEmailPayload> emailPayload = memberService.inviteMember(
                membership.getGroupId(),
                membership.getUserId(),
                inviteRequest.firstName(),
                inviteRequest.lastName(),
                inviteRequest.email()
        );

        emailPayload.ifPresent(p -> {
            try {
                emailService.sendGroupInvitationEmail(
                        p.toEmail(),
                        p.verificationRaw(),
                        p.formattedExpiry(),
                        p.groupName(),
                        p.inviterFirstName()
                );
            } catch (Exception e) {
                log.error("Invitation email failed for {} — DB record committed, resend manually. Error: {}",
                        p.toEmail(), e.getMessage(), e);
            }
        });

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

        List<MemberDto> members = memberService.findMembers(membership.getGroupId());

        return ResponseEntity.ok(members);
    }

    public record InviteRequest(String firstName, String lastName, String email) {}
}