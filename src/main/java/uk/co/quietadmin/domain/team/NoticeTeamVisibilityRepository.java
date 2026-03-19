package uk.co.quietadmin.domain.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeTeamVisibilityRepository extends JpaRepository<NoticeTeamVisibility, Long> {

    List<NoticeTeamVisibility> findByNoticeId(Long noticeId);

    void deleteByNoticeId(Long noticeId);

    boolean existsByNoticeId(Long noticeId);
}