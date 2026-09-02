package com.eeum.eeum.domain.notification.repository;

import com.eeum.eeum.domain.notification.entity.NotificationOutbox;
import com.eeum.eeum.domain.notification.enums.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    // 오래된 것부터 처리해 알림 순서가 발생 순서와 크게 어긋나지 않게 한다.
    List<NotificationOutbox> findByStatusOrderByCreatedAtAscOutboxIdAsc(
            OutboxStatus status, Limit limit);

    /**
     * 처리 대상 한 행을 잠그고 읽는다.
     *
     * <p>잠그지 않으면 "PENDING인지 확인 → 처리 → DONE" 사이에 다른 인스턴스가 같은 행을 읽어
     * 같은 알림이 두 번 만들어진다. 폴링은 {@code @SchedulerLock}이 한 인스턴스로 좁히지만
     * 그 lease는 시간이 지나면 스스로 풀리므로(lockAtMostFor), 배치가 길어지면 두 인스턴스가
     * 겹칠 수 있다 — 중복을 실제로 막는 것은 이 행 잠금이다.
     *
     * <p>대기형 잠금이다(NOWAIT 아님). 즉시 실패시키면 잠금 경합이 처리 실패로 기록되어
     * 시도 횟수가 오르고, 다섯 번이면 알림이 생성되지 않은 채 FAILED로 내려간다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM NotificationOutbox o WHERE o.outboxId = :outboxId")
    Optional<NotificationOutbox> findByIdForUpdate(@Param("outboxId") Long outboxId);

    // 처리 완료분 정리 — 남겨두면 대기 행 조회가 점점 느려진다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        DELETE FROM NotificationOutbox o
        WHERE o.status = :status AND o.processedAt < :threshold
        """)
    int deleteProcessedBefore(@Param("status") OutboxStatus status,
                              @Param("threshold") LocalDateTime threshold);
}
