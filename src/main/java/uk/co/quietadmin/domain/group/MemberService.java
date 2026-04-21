package uk.co.quietadmin.domain.group;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.quietadmin.domain.group.dto.MemberDto;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;
import uk.co.quietadmin.domain.user.UserStatus;
import uk.co.quietadmin.security.TokenHash;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final UserAccountRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final InvitationRepository invitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final GroupService groupService;

    private final SecureRandom secureRandom = new SecureRandom();

    /* ======================================================
       INVITE MEMBER
       ====================================================== */

    /**
     * Saves all DB records for an invitation and returns the email payload
     * when an invitation email should be sent, or empty if the user is already
     * active (silent group add, no email needed).
     * The caller must send the email AFTER this method returns so the
     * transaction has already committed.
     */
    @Transactional
    public Optional<InvitationEmailPayload> inviteMember(Long groupId,
                                                         Long invitedBy,
                                                         String firstName,
                                                         String lastName,
                                                         String email) {

        String normalizedEmail = email.trim().toLowerCase();

        UserAccount existingUser =
                userRepository.findByEmail(normalizedEmail).orElse(null);

        // Build shared helpers used by both invite paths
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'at' HH:mm")
                        .withZone(ZoneId.of("Europe/London"));

        QaGroup group = groupService.getGroupById(groupId)
                .orElseThrow(() -> new IllegalStateException("Group does not exist."));

        UserAccount inviter = userRepository.findById(invitedBy)
                .orElseThrow(() -> new IllegalStateException("Inviter not found."));

        // -----------------------------------------------------
        // CASE 1: USER ALREADY EXISTS
        // -----------------------------------------------------

        if (existingUser != null) {

            boolean alreadyMember = membershipRepository
                    .existsByGroupIdAndUserId(groupId, existingUser.getId());

            // INVITED but not yet activated — idempotent resend
            if (existingUser.getUserStatus() == UserStatus.INVITED) {

                if (!alreadyMember) {
                    Membership membership = new Membership();
                    membership.setGroupId(groupId);
                    membership.setUserId(existingUser.getId());
                    membership.setRole(MembershipRole.MEMBER);
                    membership.setInvitedBy(invitedBy);
                    membershipRepository.save(membership);
                }

                // Refresh token so the new email link works
                String verificationRaw = generatePublicToken();
                existingUser.setEmailVerificationToken(TokenHash.sha256(verificationRaw));
                existingUser.setEmailVerificationExpiresAt(Instant.now().plus(48, ChronoUnit.HOURS));
                userRepository.save(existingUser);

                return Optional.of(new InvitationEmailPayload(
                        normalizedEmail,
                        verificationRaw,
                        formatter.format(existingUser.getEmailVerificationExpiresAt()),
                        group.getName(),
                        inviter.getFirstName()
                ));
            }

            // Active user — reject if already a member, otherwise add silently
            if (alreadyMember) {
                throw new IllegalStateException("User already in group.");
            }

            Membership membership = new Membership();
            membership.setGroupId(groupId);
            membership.setUserId(existingUser.getId());
            membership.setRole(MembershipRole.MEMBER);
            membership.setInvitedBy(invitedBy);
            membershipRepository.save(membership);
            return Optional.empty();
        }

        // -----------------------------------------------------
        // CASE 2: BRAND NEW USER
        // -----------------------------------------------------

        String verificationRaw = generatePublicToken();
        String verificationHash = TokenHash.sha256(verificationRaw);

        UserAccount invitedUser = new UserAccount();
        invitedUser.setFirstName(firstName);
        invitedUser.setLastName(lastName);
        invitedUser.setEmail(normalizedEmail);
        invitedUser.setUserStatus(UserStatus.INVITED);
        invitedUser.setEmailVerified(false);
        invitedUser.setPasswordHash(null);
        invitedUser.setEmailVerificationToken(verificationHash);
        invitedUser.setEmailVerificationExpiresAt(
                Instant.now().plus(48, ChronoUnit.HOURS)
        );

        userRepository.save(invitedUser);

        String inviteRaw = generatePublicToken();
        String inviteHash = TokenHash.sha256(inviteRaw);

        Invitation invitation = new Invitation();
        invitation.setEmail(normalizedEmail);
        invitation.setGroupId(groupId);
        invitation.setRole(MembershipRole.MEMBER);
        invitation.setInvitedBy(invitedBy);
        invitation.setTokenHash(inviteHash);
        invitation.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));

        invitationRepository.save(invitation);

        return Optional.of(new InvitationEmailPayload(
                normalizedEmail,
                verificationRaw,    // IMPORTANT: RAW token
                formatter.format(invitedUser.getEmailVerificationExpiresAt()),
                group.getName(),
                inviter.getFirstName()
        ));
    }

    /* ======================================================
       EMAIL PAYLOAD — returned to caller for post-commit send
       ====================================================== */

    public record InvitationEmailPayload(
            String toEmail,
            String verificationRaw,
            String formattedExpiry,
            String groupName,
            String inviterFirstName
    ) {}

    /* ======================================================
       COMPLETE MEMBERSHIP AFTER REGISTRATION
       ====================================================== */

    @Transactional
    public void completeMembershipAfterActivation(UserAccount user) {

        Invitation invitation = invitationRepository
                .findTopByEmailAndAcceptedAtIsNull(user.getEmail())
                .orElse(null);

        if (invitation == null) return;

        if (!membershipRepository.existsByGroupIdAndUserId(
                invitation.getGroupId(),
                user.getId())) {

            Membership membership = new Membership();
            membership.setGroupId(invitation.getGroupId());
            membership.setUserId(user.getId());
            membership.setRole(invitation.getRole());
            membership.setInvitedBy(invitation.getInvitedBy());

            membershipRepository.save(membership);
        }

        invitation.setAcceptedAt(Instant.now());
    }

    /* ======================================================
       TOKEN GENERATOR
       ====================================================== */

    private String generatePublicToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public List<MemberDto> findMembers(Long groupId) {

        return membershipRepository.findByGroupId(groupId).stream()
                .map(m -> {
                    UserAccount u = userRepository.findById(m.getUserId())
                            .orElseThrow(() -> new IllegalStateException("User not found"));

                    return new MemberDto(
                            u.getId(),
                            u.getFirstName(),
                            u.getLastName(),
                            u.getEmail(),
                            m.getRole(),
                            u.getUserStatus()
                    );
                })
                .toList();
    }

}
