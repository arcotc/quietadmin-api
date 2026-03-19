package uk.co.quietadmin.domain.group;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.quietadmin.domain.group.dto.GroupDto;
import uk.co.quietadmin.domain.group.dto.MemberDto;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final UserAccountRepository userRepository;

    /* ======================================================
       GROUP DETAILS
       ====================================================== */

    @Transactional(readOnly = true)
    public GroupDto getGroup(Long groupId) {

        QaGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalStateException("Group not found"));

        return new GroupDto(
                group.getId(),
                group.getName(),
                group.getDescription()
        );
    }

    @Transactional
    public GroupDto updateGroup(Long groupId, String name, String description) {

        QaGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalStateException("Group not found"));

        group.setName(name);
        group.setDescription(description);

        return new GroupDto(
                group.getId(),
                group.getName(),
                group.getDescription()
        );
    }

    public Optional<QaGroup> getGroupById(Long groupId) {
        return groupRepository.findById(groupId);
    }

    /* ======================================================
       MEMBERS
       ====================================================== */

    @Transactional(readOnly = true)
    public List<MemberDto> getMembers(Long groupId) {

        List<Membership> memberships =
                membershipRepository.findByGroupId(groupId);

        return memberships.stream()
                .map(m -> {
                    UserAccount user = userRepository.findById(m.getUserId())
                            .orElseThrow(() -> new IllegalStateException("User not found"));

                    return new MemberDto(
                            user.getId(),
                            user.getFirstName(),
                            user.getLastName(),
                            user.getEmail(),
                            m.getRole(),
                            user.getUserStatus()
                    );
                })
                .toList();
    }

    @Transactional
    public void promoteToAdmin(Long groupId, Long userId) {

        Membership membership = membershipRepository
                .findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new IllegalStateException("Membership not found"));

        membership.setRole(MembershipRole.ADMIN);
    }

    @Transactional
    public void removeMember(Long groupId, Long userId, Long currentUserId) {

        if (userId.equals(currentUserId)) {
            throw new IllegalStateException("You cannot remove yourself from the group.");
        }

        Membership membership = membershipRepository
                .findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new IllegalStateException("Membership not found"));

        membershipRepository.delete(membership);
    }

    public void updateMember(Long targetUserId,
                             UpdateMemberRequest request,
                             Principal principal) {

        UserAccount currentUser = userRepository
                .findByEmailAndDeletedAtIsNull(principal.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        UserAccount target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        Membership targetMembership = membershipRepository
                .findByUserId(targetUserId)
                .orElseThrow(() -> new IllegalStateException("Target membership not found"));

        Membership currentMembership = membershipRepository
                .findByUserIdAndGroupId(
                        currentUser.getId(),
                        targetMembership.getGroupId()
                )
                .orElseThrow(() -> new IllegalStateException("Current membership not found"));

        boolean isSelf = currentUser.getId().equals(targetUserId);
        boolean isAdmin = MembershipRole.ADMIN.equals(currentMembership.getRole());

        if (!isSelf && !isAdmin) {
            throw new IllegalStateException("Not permitted");
        }

        target.setFirstName(request.firstName().trim());
        target.setLastName(request.lastName().trim());
        target.setEmail(request.email().trim().toLowerCase());

        userRepository.save(target);
    }
}