package uk.co.quietadmin.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import uk.co.quietadmin.domain.notice.Notice;
import uk.co.quietadmin.domain.notice.NoticeRepository;
import uk.co.quietadmin.domain.notice.NoticeStatus;
import uk.co.quietadmin.domain.notice.NoticeTeamVisibility;
import uk.co.quietadmin.domain.team.Team;
import uk.co.quietadmin.domain.team.TeamMembership;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NoticeRepositoryVisibilityTest {

    @Autowired NoticeRepository noticeRepository;
    @Autowired EntityManager entityManager;

    @Test
    void visibleActiveNotices_includeGlobalAndMatchingTeamOnly() {
        Team team = team("Stewards");
        entityManager.persist(team);

        TeamMembership membership = new TeamMembership();
        membership.setTeamId(team.getId());
        membership.setUserId(10L);
        entityManager.persist(membership);

        Notice global = notice("Global");
        Notice targeted = notice("Team only");
        Notice hidden = notice("Other team");
        entityManager.persist(global);
        entityManager.persist(targeted);
        entityManager.persist(hidden);

        NoticeTeamVisibility targetedVisibility = new NoticeTeamVisibility();
        targetedVisibility.setNoticeId(targeted.getId());
        targetedVisibility.setTeamId(team.getId());
        entityManager.persist(targetedVisibility);

        Team otherTeam = team("Committee");
        entityManager.persist(otherTeam);

        NoticeTeamVisibility hiddenVisibility = new NoticeTeamVisibility();
        hiddenVisibility.setNoticeId(hidden.getId());
        hiddenVisibility.setTeamId(otherTeam.getId());
        entityManager.persist(hiddenVisibility);

        entityManager.flush();
        entityManager.clear();

        List<Notice> visible = noticeRepository.findVisibleActiveNotices(
                1L,
                10L,
                Instant.now()
        );

        assertThat(visible)
                .extracting(Notice::getTitle)
                .contains("Global", "Team only")
                .doesNotContain("Other team");
    }

    @Test
    void visibleActiveNoticeById_excludesNonMatchingTeamNotice() {
        Team team = team("Stewards");
        entityManager.persist(team);

        Notice targeted = notice("Team only");
        entityManager.persist(targeted);

        NoticeTeamVisibility visibility = new NoticeTeamVisibility();
        visibility.setNoticeId(targeted.getId());
        visibility.setTeamId(team.getId());
        entityManager.persist(visibility);

        entityManager.flush();
        entityManager.clear();

        assertThat(noticeRepository.findVisibleActiveNoticeById(
                targeted.getId(),
                1L,
                99L,
                Instant.now()
        )).isEmpty();
    }

    private Notice notice(String title) {
        Notice notice = new Notice();
        notice.setGroupId(1L);
        notice.setTitle(title);
        notice.setContent("Content");
        notice.setStatus(NoticeStatus.ACTIVE);
        notice.setCreatedBy(1L);
        return notice;
    }

    private Team team(String name) {
        Team team = new Team();
        team.setGroupId(1L);
        team.setName(name);
        return team;
    }
}
