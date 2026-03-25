package uk.co.quietadmin.service.group;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.customer.CurrentUserService;
import uk.co.quietadmin.domain.group.GroupService;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.signup.PendingSignup;
import uk.co.quietadmin.domain.signup.PendingSignupRepository;
import uk.co.quietadmin.domain.signup.PendingSignup.PendingSignupStatus;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final PendingSignupRepository pendingSignupRepository;
    private final CurrentUserService currentUserService;
    private final GroupService groupService;

    public List<PendingSignup> getPendingInvites(String username) {

        Membership membership = currentUserService.getMembership(username);

        String groupName = groupService.getGroup(membership.getGroupId()).name();

        return pendingSignupRepository.findByGroupNameAndStatusIn(
                groupName,
                List.of(
                        PendingSignupStatus.PENDING_CHECKOUT,
                        PendingSignupStatus.CHECKOUT_COMPLETED
                )
        );
    }
}