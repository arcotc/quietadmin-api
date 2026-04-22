package uk.co.quietadmin.helpers;

import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.group.MembershipRole;
import uk.co.quietadmin.domain.group.QaGroup;
import uk.co.quietadmin.domain.group.StripeSubscriptionStatus;
import uk.co.quietadmin.domain.group.PlanType;
import uk.co.quietadmin.domain.notice.Notice;
import uk.co.quietadmin.domain.notice.NoticeStatus;
import uk.co.quietadmin.domain.team.Team;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserStatus;

import java.time.Instant;

/**
 * Reusable entity builders for unit and integration tests.
 */
public class TestFactory {

    public static UserAccount activeUser(long id, String email) {
        UserAccount u = new UserAccount();
        u.setId(id);
        u.setEmail(email);
        u.setFirstName("Test");
        u.setLastName("User");
        u.setPasswordHash("$2a$10$placeholder");
        u.setUserStatus(UserStatus.ACTIVE);
        u.setEmailVerified(true);
        return u;
    }

    public static Membership adminMembership(long userId, long groupId) {
        Membership m = new Membership();
        m.setId(userId * 100L);
        m.setUserId(userId);
        m.setGroupId(groupId);
        m.setRole(MembershipRole.ADMIN);
        return m;
    }

    public static Membership memberMembership(long userId, long groupId) {
        Membership m = new Membership();
        m.setId(userId * 100L + 1);
        m.setUserId(userId);
        m.setGroupId(groupId);
        m.setRole(MembershipRole.MEMBER);
        return m;
    }

    public static QaGroup activeGroup(long id, String name) {
        QaGroup g = new QaGroup();
        g.setId(id);
        g.setName(name);
        g.setSlug(name.toLowerCase().replace(" ", "-"));
        g.setCreatedBy(1L);
        g.setPlanType(PlanType.STANDARD);
        g.setStripeSubscriptionStatus(StripeSubscriptionStatus.TRIALING);
        g.setStripeTrialEnd(Instant.now().plusSeconds(86400 * 14));
        return g;
    }

    public static Team team(long id, long groupId, String name) {
        Team t = new Team();
        t.setId(id);
        t.setGroupId(groupId);
        t.setName(name);
        return t;
    }

    public static Notice draftNotice(long id, long groupId, long createdBy) {
        Notice n = new Notice();
        n.setId(id);
        n.setGroupId(groupId);
        n.setTitle("Test Notice");
        n.setContent("Test content");
        n.setStatus(NoticeStatus.DRAFT);
        n.setCreatedBy(createdBy);
        return n;
    }

    public static Notice activeNotice(long id, long groupId, long createdBy) {
        Notice n = draftNotice(id, groupId, createdBy);
        n.setStatus(NoticeStatus.ACTIVE);
        return n;
    }

    public static Notice expiredNotice(long id, long groupId, long createdBy) {
        Notice n = draftNotice(id, groupId, createdBy);
        n.setStatus(NoticeStatus.EXPIRED);
        return n;
    }
}
