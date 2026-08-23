package com.eeum.eeum.application.chat.listener;

import com.eeum.eeum.application.chat.dto.response.ChatRoomClosedResponseDto;
import com.eeum.eeum.domain.chat.event.ChatMessageBroadcastEvent;
import com.eeum.eeum.domain.chat.event.ChatRoomClosedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 채팅 메시지 WebSocket 브로드캐스트 리스너.
 * DB 커밋 완료 후 동기 실행(@Async 없음) — 클라이언트에 일관된 상태를 전달하기 위해 커밋 후 전송.
 * TEXT/IMAGE / 삭제 / SYSTEM 메시지: 이 리스너가 처리
 */
/**
 * 인메모리 브로커로 직접 밀어내는 경로 — 브로커가 있는 인스턴스에서만 동작한다.
 * TODO: Redis Pub/Sub 중계를 붙이면 이 조건을 없애고 모든 인스턴스에서 발행하게 한다.
 */
@ConditionalOnProperty(name = "eeum.realtime.enabled", havingValue = "true", matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class ChatBroadcastEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBroadcast(ChatMessageBroadcastEvent event) {
        messagingTemplate.convertAndSend(
                "/sub/chat/rooms/" + event.roomId(), event.payload());
    }

    // 채팅방 종료 통지 — 남아있는 구독자가 즉시 방을 닫도록 별도 채널로 전송.
    // 종료 후에는 신규 SUBSCRIBE가 차단되지만 이미 열려 있는 세션은 끊기지 않으므로 필요하다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomClosed(ChatRoomClosedEvent event) {
        messagingTemplate.convertAndSend(
                "/sub/chat/rooms/" + event.roomId() + "/closed",
                ChatRoomClosedResponseDto.of(
                        event.roomId(), event.closedByAccountId(), event.closedAt()));
    }
}
