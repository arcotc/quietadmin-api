package uk.co.quietadmin.service.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.group.MembershipRepository;
import uk.co.quietadmin.domain.group.QaGroup;
import uk.co.quietadmin.domain.group.QaGroupRepository;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;
import uk.co.quietadmin.domain.user.UserStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private final UserAccountRepository userRepository;
    private final QaGroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserAccountRepository userRepository,
            QaGroupRepository groupRepository,
            MembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccount register(String email, String rawPassword) {

        String normalizedEmail = email.toLowerCase().trim();

        userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .ifPresent(u -> {
                    throw new RuntimeException("Email already registered");
                });

        // 1️⃣ Create user
        UserAccount user = new UserAccount();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setStatus(UserStatus.ACTIVE);

        user = userRepository.save(user);

        // 2️⃣ Create group
        QaGroup group = new QaGroup();
        group.setName(defaultGroupName(normalizedEmail));
        group.setSlug(generateSlug(normalizedEmail));
        group.setCreatedBy(user.getId());
        group.setTrialEndsAt(Instant.now().plus(14, ChronoUnit.DAYS));

        group = groupRepository.save(group);

        // 3️⃣ Create membership as ADMIN
        Membership membership = new Membership();
        membership.setUserId(user.getId());
        membership.setGroupId(group.getId());
        membership.setRole("ADMIN");

        membershipRepository.save(membership);

        return user;
    }

    private String defaultGroupName(String email) {
        return email.split("@")[0] + "'s Group";
    }

    private String generateSlug(String email) {
        return email.split("@")[0]
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "-")
                + "-" + System.currentTimeMillis();
    }
}