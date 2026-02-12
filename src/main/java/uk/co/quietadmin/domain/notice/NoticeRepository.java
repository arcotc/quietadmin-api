package uk.co.quietadmin.domain.notice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    Optional<Notice> findByIdAndGroupId(Long id, Long groupId);

    @Query("""
        SELECT n FROM Notice n
        WHERE n.groupId = :groupId
        AND (n.expiresAt IS NULL OR n.expiresAt > :now)
        ORDER BY
            CASE WHEN n.expiresAt IS NULL THEN 1 ELSE 0 END,
            n.expiresAt ASC,
            n.createdAt DESC
    """)
    List<Notice> findActiveNotices(
            @Param("groupId") Long groupId,
            @Param("now") Instant now
    );
}