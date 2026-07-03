package com.eeum.eeum.application.ai.listener;

import com.eeum.eeum.domain.ai.event.AiMessageSentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 1차 MVP: 실제 알림톡/FCM 발송 대신 로그만 기록한다.
 * 2차에서 이 리스너를 Notification 도메인/PushAdapter와 연결한다.
 */
@Slf4j
@Component
public class AiMessageEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAiMessageSent(AiMessageSentEvent event) {
        log.info("[AI-MANAGER] 메시지 발송 이벤트 - messageId={}, storeId={}, type={}, channel={}, scheduled={}",
                event.messageId(), event.storeId(), event.type(), event.channel(), event.scheduled());
    }
}
