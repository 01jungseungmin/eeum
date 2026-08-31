package com.eeum.eeum.domain.notification.repository;

import com.eeum.eeum.domain.notification.entity.NotificationOutbox;
import com.eeum.eeum.domain.notification.enums.OutboxStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    // 오래된 것부터 처리해 알림 순서가 발생 순서와 크게 어긋나지 않게 한다.
    List<NotificationOutbox> findByStatusOrderByCreatedAtAscOutboxIdAsc(
            OutboxStatus status, Limit limit);

    // 처리 완료분 정리 — 남겨두면 대기 행 조회가 점점 느려진다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        DELETE FROM NotificationOutbox o
        WHERE o.status = :status AND o.processedAt < :threshold
        """)
    int deleteProcessedBefore(@Param("status") OutboxStatus status,
                              @Param("threshold") LocalDateTime threshold);
}
