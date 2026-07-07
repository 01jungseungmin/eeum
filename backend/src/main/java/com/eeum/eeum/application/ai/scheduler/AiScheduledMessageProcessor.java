package com.eeum.eeum.application.ai.scheduler;

import com.eeum.eeum.domain.ai.entity.AiActionLog;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 예약 메시지 개별 처리 — 메시지 단위 트랜잭션 분리로 한 건 실패가 다른 건에 영향 주지 않게 한다
@Slf4j
@Component
@RequiredArgsConstructor
public class AiScheduledMessageProcessor {

    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final AiActionLogRepository aiActionLogRepository;

    // 발송 전 상태 재확인 후 SENT 전이 — 전이했으면 true
    @Transactional
    public boolean transitionToSent(Long messageId, LocalDateTime now) {
        AiGeneratedMessage message = aiGeneratedMessageRepository.findById(messageId).orElse(null);
        if (message == null || !message.isDispatchable(now)) {
            return false; // 취소/수정/이미 발송된 메시지는 건너뜀
        }
        message.send(now);
        aiActionLogRepository.save(AiActionLog.record(
                message.getStore(), message.getOwnerAccount(), AiActionType.MESSAGE_SENT,
                message.getType().name(), messageId, "예약 발송 처리 (스케줄러)"));
        return true;
    }

    // 발송 실패 시 재시도 카운트 증가, 최대 재시도 초과 시 FAILED 확정
    @Transactional
    public void recordFailure(Long messageId, int maxRetryCount) {
        AiGeneratedMessage message = aiGeneratedMessageRepository.findById(messageId).orElse(null);
        if (message == null || message.getStatus() != AiMessageStatus.SCHEDULED) {
            return;
        }
        message.increaseRetryCount();
        if (message.getRetryCount() >= maxRetryCount) {
            message.markFailed();
            log.warn("[AI-SCHEDULER] 최대 재시도 초과 — FAILED 처리: messageId={}, retryCount={}",
                    messageId, message.getRetryCount());
        }
    }
}
