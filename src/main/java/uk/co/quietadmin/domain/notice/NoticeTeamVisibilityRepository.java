package uk.co.quietadmin.domain.notice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeTeamVisibilityRepository extends JpaRepository<NoticeTeamVisibility, Long> {

    List<NoticeTeamVisibility> findByNoticeId(Long noticeId);

    List<NoticeTeamVisibility> findByNoticeIdIn(List<Long> noticeIds);

    void deleteByNoticeId(Long noticeId);

    boolean existsByNoticeId(Long noticeId);
}
