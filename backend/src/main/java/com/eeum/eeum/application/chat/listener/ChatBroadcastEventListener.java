package com.eeum.eeum.application.chat.listener;

import com.eeum.eeum.domain.chat.event.ChatMessageBroadcastEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 채팅 메시지 WebSocket 브로드캐스트 리스너.
 * DB 커밋 완료 후 동기 실행(@Async 없음) — 클라이언트에 일관된 상태를 전달하기 위해 커밋 후 전송.
 * TEXT/IMAGE / 삭제 / SYSTEM 메시지: 이 리스너가 처리
 */
@Component
@RequiredArgsConstructor
public class ChatBroadcastEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBroadcast(ChatMessageBroadcastEvent event) {
        messagingTemplate.convertAndSend(
                "/sub/chat/rooms/" + event.roomId(), event.payload());
    }
}
