package com.eeum.eeum.application.ai.listener;

import com.eeum.eeum.application.ai.service.AiMessageCommandExecutor;
import com.eeum.eeum.application.ai.service.AiMessageDispatchService;
import com.eeum.eeum.domain.ai.event.AiMessageSentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 2차: 발송 이벤트를 실제 디스패처와 연결.
 * 즉시 발송(SENT)은 커밋 후 비동기로 수신자 발송을 수행하고,
 * 예약 발송(SCHEDULED)은 스케줄러(AiScheduledMessageScheduler)가 발송 시점에 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiMessageEventListener {

    private final AiMessageDispatchService aiMessageDispatchService;
    private final AiMessageCommandExecutor aiMessageCommandExecutor;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAiMessageSent(AiMessageSentEvent event) {
        log.info("[AI-MANAGER] 메시지 발송 이벤트 - messageId={}, storeId={}, type={}, channel={}, scheduled={}",
                event.messageId(), event.storeId(), event.type(), event.channel(), event.scheduled());
        if (event.scheduled()) {
            return; // 예약 발송은 스케줄러가 처리
        }
        try {
            aiMessageDispatchService.dispatch(event.messageId());
        } catch (Exception e) {
            log.error("[AI-MANAGER] 메시지 디스패치 실패: messageId={}", event.messageId(), e);
            try {
                aiMessageCommandExecutor.markFailedInTx(event.messageId());
            } catch (Exception markEx) {
                log.error("[AI-MANAGER] FAILED 상태 전이 실패: messageId={}", event.messageId(), markEx);
            }
        }
    }
}
