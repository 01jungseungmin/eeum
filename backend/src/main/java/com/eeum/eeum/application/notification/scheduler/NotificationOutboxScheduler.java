package com.eeum.eeum.application.notification.scheduler;

import com.eeum.eeum.application.notification.service.NotificationOutboxDispatcher;
import com.eeum.eeum.domain.notification.entity.NotificationOutbox;
import com.eeum.eeum.domain.notification.enums.OutboxStatus;
import com.eeum.eeum.domain.notification.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * outbox에 쌓인 알림 요청을 처리한다.
 *
 * <p>예전에는 {@code AFTER_COMMIT} + {@code @Async}가 이 일을 했는데, 비동기 풀이 포화되면
 * 작업이 버려져 알림이 만들어지지 않았다. 지금은 원 트랜잭션에서 커밋된 행을 읽어 처리하므로
 * 풀 상태와 무관하고, 인스턴스가 죽어도 다음 주기에 이어서 한다.
 *
 * <p>스케줄러 스레드가 기본 1개라 한 번에 처리하는 양을 제한한다. 밀린 행이 많아도
 * 이 스케줄러가 오래 붙잡고 있으면 다른 스케줄러가 전부 밀린다 — 다음 주기에 마저 한다.
 *
 * <p>행마다 트랜잭션을 나눈다. 하나로 묶으면 중간 한 건이 실패할 때 이미 처리한 알림까지
 * 함께 롤백되고, 재시도에서 같은 실패를 반복하며 뒤의 정상 알림이 영원히 막힌다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxScheduler {

    private static final int BATCH_SIZE = 100;
    private static final int DONE_RETENTION_HOURS = 24;

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationOutboxDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${eeum.notification.outbox-poll-interval-ms:1000}")
    @SchedulerLock(name = "dispatchNotificationOutbox", lockAtMostFor = "PT5M")
    public void dispatchPending() {
        List<NotificationOutbox> pending = outboxRepository
                .findByStatusOrderByCreatedAtAscOutboxIdAsc(
                        OutboxStatus.PENDING, Limit.of(BATCH_SIZE));
        if (pending.isEmpty()) {
            return;
        }

        for (NotificationOutbox outbox : pending) {
            Long outboxId = outbox.getOutboxId();
            try {
                dispatcher.dispatch(outboxId);
            } catch (Exception e) {
                // 처리 트랜잭션은 이미 롤백됐다. 시도 횟수는 별도 트랜잭션에 남긴다.
                dispatcher.recordFailure(outboxId, e);
            }
        }
    }

    /**
     * 처리 완료분 정리.
     *
     * <p>남겨두면 대기 행 조회가 점점 느려진다 — 인덱스 선두가 status라 DONE이 쌓여도
     * 탐색 자체는 좁지만, 테이블이 무한히 커지는 것을 막을 이유는 그것만이 아니다.
     *
     * <p>FAILED는 지우지 않는다. 알림이 끝내 생성되지 않은 기록이라 조사할 근거로 남긴다.
     */
    @Scheduled(cron = "0 20 4 * * *")
    @SchedulerLock(name = "cleanupNotificationOutbox", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public void cleanupProcessed() {
        int deleted = outboxRepository.deleteProcessedBefore(
                OutboxStatus.DONE, LocalDateTime.now().minusHours(DONE_RETENTION_HOURS));
        if (deleted > 0) {
            log.info("처리 완료된 알림 outbox 정리: {}건", deleted);
        }
    }
}
