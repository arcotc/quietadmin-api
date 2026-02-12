package uk.co.quietadmin.domain.customer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.group.MembershipRepository;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserAccountRepository userRepo;
    private final MembershipRepository membershipRepo;

    public Long getCurrentGroupId(String email) {

        UserAccount user = userRepo.findByEmail(email)
                .orElseThrow();

        return membershipRepo.findByUserId(user.getId())
                .map(Membership::getGroupId)
                .orElseThrow();
    }

    public Long getCurrentUserId(String email) {
        return userRepo.findByEmail(email)
                .map(UserAccount::getId)
                .orElseThrow();
    }
}