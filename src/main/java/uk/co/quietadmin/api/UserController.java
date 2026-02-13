package uk.co.quietadmin.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.group.MembershipRepository;
import uk.co.quietadmin.domain.group.QaGroup;
import uk.co.quietadmin.domain.group.QaGroupRepository;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;

import java.security.Principal;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserAccountRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final QaGroupRepository qaGroupRepository;

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        String email = principal.getName();

        UserAccount user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        Membership membership = membershipRepository
                .findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Membership not found"));

        QaGroup group = qaGroupRepository
                .findById(membership.getGroupId())
                .orElseThrow(() -> new IllegalStateException("Group not found"));

        return ResponseEntity.ok(
                new MeResponse(
                        user.getId(),
                        user.getEmail(),
                        user.getFirstName(),
                        user.getLastName(),
                        group.getId(),
                        group.getName(),
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
            String groupName,
            String membershipRole,
            boolean platformAdmin
    ) {}
}