package uk.co.quietadmin.domain.notice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Optional<Notice> findByIdAndGroupId(Long id, Long groupId);

    /* ======================================================
       ACTIVE (CONSISTENT ORDERING)
       ====================================================== */

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
    List<Notice> findActiveNotices(
            @Param("groupId") Long groupId,
            @Param("now") Instant now
    );

    /* ======================================================
       DRAFT (CONSISTENT ORDERING)
       ====================================================== */

    @Query("""
        SELECT n FROM Notice n
        WHERE n.groupId = :groupId
          AND n.status = uk.co.quietadmin.domain.notice.NoticeStatus.DRAFT
        ORDER BY
          CASE WHEN n.expiresAt IS NULL THEN 1 ELSE 0 END,
          n.expiresAt ASC,
          n.createdAt DESC
    """)
    List<Notice> findDraftNotices(@Param("groupId") Long groupId);

    /* ======================================================
       EXPIRED (CONSISTENT ORDERING)
       ====================================================== */

    @Query("""
        SELECT n FROM Notice n
        WHERE n.groupId = :groupId
          AND n.status = uk.co.quietadmin.domain.notice.NoticeStatus.EXPIRED
        ORDER BY
          CASE WHEN n.expiresAt IS NULL THEN 1 ELSE 0 END,
          n.expiresAt ASC,
          n.createdAt DESC
    """)
    List<Notice> findExpiredNotices(@Param("groupId") Long groupId);

    /* ======================================================
       GLOBAL EXPIRY JOB (NO ORDER NEEDED)
       ====================================================== */

    @Query("""
        SELECT n FROM Notice n
        WHERE n.status = uk.co.quietadmin.domain.notice.NoticeStatus.ACTIVE
          AND n.expiresAt IS NOT NULL
          AND n.expiresAt <= :now
    """)
    List<Notice> findAllActiveButExpired(@Param("now") Instant now);

    @Modifying
    @Query("""
        UPDATE Notice n
        SET n.status = uk.co.quietadmin.domain.notice.NoticeStatus.EXPIRED
        WHERE n.status = uk.co.quietadmin.domain.notice.NoticeStatus.ACTIVE
          AND n.expiresAt IS NOT NULL
          AND n.expiresAt <= :now
    """)
    int bulkExpire(@Param("now") Instant now);

    /* ======================================================
       GENERIC GROUP LIST (MATCH SAME ORDERING)
       ====================================================== */

    @Query("""
        SELECT n FROM Notice n
        WHERE n.groupId = :groupId
        ORDER BY
          CASE WHEN n.expiresAt IS NULL THEN 1 ELSE 0 END,
          n.expiresAt ASC,
          n.createdAt DESC
    """)
    List<Notice> findByGroupIdOrdered(@Param("groupId") Long groupId);

    /* ======================================================
       REMINDER QUERY (NO UI ORDER NEEDED)
       ====================================================== */

    @Query("""
        SELECT n FROM Notice n
        WHERE n.groupId = :groupId
          AND n.status = uk.co.quietadmin.domain.notice.NoticeStatus.ACTIVE
          AND n.expiresAt IS NOT NULL
          AND n.expiryReminderSentAt IS NULL
          AND n.expiresAt BETWEEN :from AND :to
    """)
    List<Notice> findNoticesExpiringBetweenForGroup(
            @Param("groupId") Long groupId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}