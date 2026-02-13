package uk.co.quietadmin.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.group.MembershipRepository;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;

import java.security.Principal;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserAccountRepository userRepository;
    private final MembershipRepository membershipRepository;

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        String email = principal.getName();

        UserAccount user = userRepository.findByEmail(email)
                .orElseThrow();

        Membership membership = membershipRepository
                .findByUserId(user.getId())
                .orElseThrow();

        return ResponseEntity.ok(
                new MeResponse(
                        user.getId(),
                        user.getEmail(),
                        user.getFirstName(),
                        user.getLastName(),
                        membership.getGroupId(),
                        membership.getRole(),
                        user.isPlatformAdmin()
                )
        );
    }

    public record MeResponse(
            Long id,
            String email,
            String firstName,
            String lastName,
            Long groupId,
            String membershipRole,
            boolean platformAdmin
    ) {}
}