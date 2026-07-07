package com.eeum.eeum.application.ai.scheduler;

import com.eeum.eeum.application.ai.service.AiMessageDispatchService;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.exception.BusinessException;
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
    private static final Duration JOB_LOCK_LEASE = Duration.ofSeconds(55);

    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final AiScheduledMessageProcessor processor;
    private final AiMessageDispatchService dispatchService;
    private final RedisLockService redisLockService;

    @Scheduled(fixedDelay = 60_000)
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
                // 메시지 단위 트랜잭션 — 상태 재확인 후 SENT 전이, 이후 수신자 발송
                if (processor.transitionToSent(messageId, now)) {
                    dispatchService.dispatch(messageId);
                }
            } catch (Exception e) {
                log.warn("[AI-SCHEDULER] 예약 발송 실패 — 재시도 예약: messageId={}", messageId, e);
                try {
                    processor.recordFailure(messageId, MAX_RETRY_COUNT);
                } catch (Exception recordError) {
                    log.error("[AI-SCHEDULER] 실패 기록 중 오류: messageId={}", messageId, recordError);
                }
            }
        }
    }
}
