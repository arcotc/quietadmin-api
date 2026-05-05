package uk.co.quietadmin.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.domain.group.*;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;
import uk.co.quietadmin.service.PasswordResetService;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserAccountRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final QaGroupRepository qaGroupRepository;
    private final PasswordResetService passwordResetService;

    /* ======================================================
       CURRENT USER
       ====================================================== */

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        UserAccount user = userRepository.findByEmailAndDeletedAtIsNull(principal.getName())
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

    /* ======================================================
       ADMIN: SEND PASSWORD RESET
       ====================================================== */

    @PostMapping("/members/{userId}/password-reset")
    public ResponseEntity<?> sendPasswordReset(
            @PathVariable Long userId,
            Principal principal
    ) {

        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        UserAccount currentUser = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        // Self-reset is always allowed
        if (currentUser.getId().equals(userId)) {
            passwordResetService.createResetTokenAndSendEmail(currentUser);
            return ResponseEntity.ok().build();
        }

        // Otherwise must be admin of same group
        Membership currentMembership = membershipRepository
                .findByUserId(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("Membership not found"));

        boolean isAdmin = MembershipRole.ADMIN.name()
                .equals(String.valueOf(currentMembership.getRole()));

        if (!isAdmin) {
            return ResponseEntity.status(403).build();
        }

        UserAccount targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Target user not found"));

        Membership targetMembership = membershipRepository
                .findByUserId(targetUser.getId())
                .orElseThrow(() -> new IllegalStateException("Target membership not found"));

        if (!currentMembership.getGroupId()
                .equals(targetMembership.getGroupId())) {
            return ResponseEntity.status(403).build();
        }

        passwordResetService.createResetTokenAndSendEmail(targetUser);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/auth/password-reset/request")
    public ResponseEntity<?> requestPasswordReset(
            @RequestBody PasswordResetRequest request
    ) {

        if (request.email() == null || request.email().isBlank()) {
            return ResponseEntity.ok().build();
        }

        userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .ifPresent(passwordResetService::createResetTokenAndSendEmail);

        // Always 200 — do not reveal whether email exists
        return ResponseEntity.ok().build();
    }

    public record PasswordResetRequest(String email) {}

    /* ======================================================
       PUBLIC: CONFIRM PASSWORD RESET
       ====================================================== */

    @PostMapping("/auth/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(
            @RequestBody PasswordResetConfirmRequest request
    ) {

        if (request.token() == null || request.token().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (request.newPassword() == null || request.newPassword().length() < 8) {
            return ResponseEntity.badRequest().build();
        }

        try {
            passwordResetService.confirmReset(
                    request.token(),
                    request.newPassword()
            );
        } catch (Exception e) {
            // Avoid leaking token validity details
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok().build();
    }

    /* ======================================================
       RECORDS
       ====================================================== */

    public record MeResponse(
            Long id,
            String email,
            String firstName,
            String lastName,
            Long groupId,
            String groupName,
            MembershipRole membershipRole,
            boolean platformAdmin
    ) {}

    public record PasswordResetConfirmRequest(
            String token,
            String newPassword
    ) {}
}