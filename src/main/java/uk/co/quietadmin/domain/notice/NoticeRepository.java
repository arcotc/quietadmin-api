package uk.co.quietadmin.domain.notice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    List<Notice> findByGroupIdAndExpiresAtAfterOrExpiresAtIsNullOrderByExpiresAtAscCreatedAtDesc(
            Long groupId,
            Instant now
    );
}