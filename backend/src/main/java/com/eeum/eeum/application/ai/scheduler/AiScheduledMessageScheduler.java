package com.eeum.eeum.application.ai.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.eeum.eeum.application.ai.service.AiMessageDispatchService;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 예약 발송 스케줄러 — 1분 주기로 scheduledAt이 지난 SCHEDULED 메시지를 발송한다.
 * - Redis 락으로 다중 인스턴스 중복 실행 방지
 * - 한 번에 최대 50건 처리 (다음 주기에 이어서 처리)
 * - 실패 시 다음 주기에 재시도, 최대 3회 초과하면 FAILED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiScheduledMessageScheduler {

    private static final int BATCH_LIMIT = 50;
    private static final int MAX_RETRY_COUNT = 3;
    // 50건 배치 × 건당 다수 수신자 동기 FCM/알림톡 호출이면 55초를 넘기기 쉽다 — 락이 배치 도중 만료되면
    // 다른 인스턴스가 같은 배치를 중복 처리할 수 있으므로 실제 처리 시간보다 넉넉하게 잡는다.
    private static final Duration JOB_LOCK_LEASE = Duration.ofMinutes(10);

    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final AiScheduledMessageProcessor processor;
    private final AiMessageDispatchService dispatchService;
    private final RedisLockService redisLockService;

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "dispatchScheduledMessages", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void dispatchScheduledMessages() {
        try {
            redisLockService.executeWithLock(LockKeys.aiScheduledMessageJob(), JOB_LOCK_LEASE, this::processBatch);
        } catch (BusinessException e) {
            // 다른 인스턴스가 실행 중 — 이번 주기는 건너뜀
            log.debug("[AI-SCHEDULER] 락 획득 실패 — 이번 주기 스킵");
        }
    }

    private void processBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<AiGeneratedMessage> targets = aiGeneratedMessageRepository
                .findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        AiMessageStatus.SCHEDULED, now, PageRequest.of(0, BATCH_LIMIT));
        if (targets.isEmpty()) {
            return;
        }
        log.info("[AI-SCHEDULER] 예약 발송 대상 {}건 처리 시작", targets.size());

        for (AiGeneratedMessage target : targets) {
            Long messageId = target.getAiGeneratedMessageId();
            try {
                // 취소/수정으로 더 이상 발송 대상이 아니게 된 메시지는 건너뜀 (상태는 아직 바꾸지 않음)
                if (!processor.isDispatchable(messageId, now)) {
                    continue;
                }
                // SCHEDULED 상태를 유지한 채 실제 발송을 먼저 시도 — 실패하면 recordFailure가 재시도 카운트를
                // 정상적으로 올릴 수 있어야 하므로, 발송 성공이 확정된 뒤에만 SENT로 전이한다
                dispatchService.dispatch(messageId);
                processor.markSent(messageId, now);
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.LOCK_ACQUIRE_FAILED) {
                    // 일시적 락 경합 — 실제 발송 실패가 아니므로 재시도 카운트를 소진시키지 않고 다음 주기에 그대로 재시도
                    log.debug("[AI-SCHEDULER] 락 경합으로 이번 주기 스킵(재시도 카운트 미증가): messageId={}", messageId);
                    continue;
                }
                recordFailureSafely(messageId, e);
            } catch (Exception e) {
                recordFailureSafely(messageId, e);
            }
        }
    }

    private void recordFailureSafely(Long messageId, Exception cause) {
        log.warn("[AI-SCHEDULER] 예약 발송 실패 — 재시도 예약: messageId={}", messageId, cause);
        try {
            processor.recordFailure(messageId, MAX_RETRY_COUNT);
        } catch (Exception recordError) {
            log.error("[AI-SCHEDULER] 실패 기록 중 오류: messageId={}", messageId, recordError);
        }
    }
}
