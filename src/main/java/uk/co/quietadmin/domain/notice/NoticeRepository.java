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
          AND n.status = uk.co.quietadmin.domain.notice.NoticeStatus.ACTIVE
          AND (n.expiresAt IS NULL OR n.expiresAt > :now)
        ORDER BY
          CASE WHEN n.expiresAt IS NULL THEN 1 ELSE 0 END,
          n.expiresAt ASC,
          n.createdAt DESC
    """)
    List<Notice> findActiveNotices(@Param("groupId") Long groupId,
                                   @Param("now") Instant now);

    @Query("""
        SELECT n FROM Notice n
        WHERE n.groupId = :groupId
          AND n.status = uk.co.quietadmin.domain.notice.NoticeStatus.DRAFT
        ORDER BY n.updatedAt DESC
    """)
    List<Notice> findDraftNotices(@Param("groupId") Long groupId);

    @Query("""
        SELECT n FROM Notice n
        WHERE n.status = uk.co.quietadmin.domain.notice.NoticeStatus.ACTIVE
          AND n.expiresAt IS NOT NULL
          AND n.expiresAt <= :now
    """)
    List<Notice> findActiveButExpired(@Param("now") Instant now);

    List<Notice> findByGroupIdOrderByUpdatedAtDesc(Long groupId);
}