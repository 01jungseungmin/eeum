package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.domain.chat.event.ChatMessageSentEvent;
import com.eeum.eeum.domain.notification.entity.NotificationOutbox;
import com.eeum.eeum.domain.notification.repository.NotificationOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * outbox 행 하나를 처리한다.
 *
 * <p>알림 생성과 상태 전이를 <b>한 트랜잭션</b>에 묶는 것이 이 클래스의 존재 이유다.
 * 나눠 두면 알림만 만들어지고 DONE을 못 남기는 창이 생기고, 그 행은 다음 주기에 다시 처리돼
 * 같은 알림이 두 번 간다. 함께 커밋하면 중간에 죽어도 둘 다 롤백돼 재시도가 안전해진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxDispatcher {

    public static final String CHAT_MESSAGE_SENT = "CHAT_MESSAGE_SENT";

    private final NotificationOutboxRepository outboxRepository;
    private final ChatNotificationProcessor chatNotificationProcessor;
    private final ObjectMapper objectMapper;

    @Transactional
    public void dispatch(Long outboxId) throws Exception {
        NotificationOutbox outbox = outboxRepository.findById(outboxId).orElse(null);
        if (outbox == null || outbox.getStatus() != com.eeum.eeum.domain.notification.enums.OutboxStatus.PENDING) {
            return;   // 다른 인스턴스가 이미 처리했거나 정리됐다
        }

        handle(outbox);
        outbox.markDone();
    }

    /**
     * 실패 기록은 <b>별도 트랜잭션</b>이다. 처리 트랜잭션이 롤백된 뒤에 실행되므로
     * 같은 트랜잭션에 두면 시도 횟수까지 함께 사라져 영원히 재시도한다.
     */
    @Transactional
    public void recordFailure(Long outboxId, Exception error) {
        outboxRepository.findById(outboxId).ifPresent(outbox -> {
            outbox.recordFailure(error.toString());
            if (outbox.isExhausted()) {
                log.error("알림 outbox 재시도 한도 초과 — 알림이 생성되지 않았다: outboxId={}, type={}",
                        outboxId, outbox.getEventType(), error);
            } else {
                log.warn("알림 outbox 처리 실패, 재시도한다: outboxId={}, attempt={}",
                        outboxId, outbox.getAttemptCount(), error);
            }
        });
    }

    // 새 이벤트를 추가하면 여기에 분기를 더한다. payload는 스칼라만 담은 record여야 한다.
    private void handle(NotificationOutbox outbox) throws Exception {
        if (CHAT_MESSAGE_SENT.equals(outbox.getEventType())) {
            chatNotificationProcessor.process(
                    objectMapper.readValue(outbox.getPayload(), ChatMessageSentEvent.class));
            return;
        }
        throw new IllegalStateException("처리기가 없는 outbox 이벤트: " + outbox.getEventType());
    }
}
