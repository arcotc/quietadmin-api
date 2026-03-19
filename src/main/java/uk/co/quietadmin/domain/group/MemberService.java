package uk.co.quietadmin.domain.group;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.quietadmin.domain.group.dto.MemberDto;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;
import uk.co.quietadmin.domain.user.UserStatus;
import uk.co.quietadmin.security.TokenHash;
import uk.co.quietadmin.service.mail.EmailService;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final UserAccountRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final InvitationRepository invitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final GroupService groupService;

    private final SecureRandom secureRandom = new SecureRandom();

    /* ======================================================
       INVITE MEMBER
       ====================================================== */

    @Transactional
    public void inviteMember(Long groupId,
                             Long invitedBy,
                             String firstName,
                             String lastName,
                             String email) {

        String normalizedEmail = email.trim().toLowerCase();

        UserAccount existingUser =
                userRepository.findByEmail(normalizedEmail).orElse(null);

        // -----------------------------------------------------
        // CASE 1: USER ALREADY EXISTS
        // -----------------------------------------------------

        if (existingUser != null) {

            if (membershipRepository.existsByGroupIdAndUserId(groupId, existingUser.getId())) {
                throw new IllegalStateException("User already in group.");
            }

            Membership membership = new Membership();
            membership.setGroupId(groupId);
            membership.setUserId(existingUser.getId());
            membership.setRole(MembershipRole.MEMBER);
            membership.setInvitedBy(invitedBy);

            membershipRepository.save(membership);
            return;
        }

        // -----------------------------------------------------
        // CASE 2: NEW INVITED USER
        // -----------------------------------------------------

        // Generate verification token (RAW + HASH)
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

        // -----------------------------------------------------
        // Create Invitation Record (Group Metadata)
        // -----------------------------------------------------

        String inviteRaw = generatePublicToken();
        String inviteHash = TokenHash.sha256(inviteRaw);

        Invitation invitation = new Invitation();
        invitation.setEmail(normalizedEmail);
        invitation.setGroupId(groupId);
        invitation.setRole(MembershipRole.MEMBER);
        invitation.setInvitedBy(invitedBy);
        invitation.setTokenHash(inviteHash);
        invitation.setExpiresAt(
                Instant.now().plus(7, ChronoUnit.DAYS)
        );

        invitationRepository.save(invitation);

        // -----------------------------------------------------
        // Send Email
        // -----------------------------------------------------

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'at' HH:mm")
                        .withZone(ZoneId.of("Europe/London"));

        String formattedExpiry =
                formatter.format(invitedUser.getEmailVerificationExpiresAt());

        QaGroup group = groupService.getGroupById(groupId)
                .orElseThrow(() ->
                        new IllegalStateException("Group does not exist."));

        UserAccount inviter = userRepository.findById(invitedBy)
                .orElseThrow(() ->
                        new IllegalStateException("Inviter not found."));

        emailService.sendGroupInvitationEmail(
                normalizedEmail,
                verificationRaw,          // IMPORTANT: RAW
                formattedExpiry,
                group.getName(),
                inviter.getFirstName()
        );
    }

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
                .toList(); // ✅ FIXED
    }
}