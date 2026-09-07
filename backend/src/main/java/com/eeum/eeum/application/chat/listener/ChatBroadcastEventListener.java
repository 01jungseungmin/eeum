package com.eeum.eeum.application.chat.listener;

import com.eeum.eeum.application.chat.dto.response.ChatRoomClosedResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;
import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.domain.chat.event.ChatMessageBroadcastEvent;
import com.eeum.eeum.domain.chat.event.ChatRoomClosedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.eeum.eeum.infrastructure.realtime.RealtimeRelayPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;

/**
 * 채팅 메시지 WebSocket 브로드캐스트 리스너.
 * DB 커밋 후 단일 FIFO 비동기 워커에서 실행해, 외부 S3 호출이 요청 스레드를 점유하지 않게 한다.
 * TEXT/IMAGE / 삭제 / SYSTEM 메시지: 이 리스너가 처리
 *
 * <p>브로커로 직접 밀지 않고 Redis로 중계한다. 메시지는 REST·STOMP 아무 인스턴스에서나
 * 만들어지지만 구독자는 실시간 인스턴스에만 붙어 있어, 직접 밀면 다른 인스턴스의 구독자에게
 * 닿지 못한다. 그래서 이 리스너는 모든 인스턴스에서 동작한다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatBroadcastEventListener {

    private final RealtimeRelayPublisher realtimeRelayPublisher;
    private final FileStorageService fileStorageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("chatBroadcastTaskExecutor")
    public void onBroadcast(ChatMessageBroadcastEvent event) {
        // Redis로 중계한다 — 이 메시지를 만든 인스턴스와 구독자가 붙은 인스턴스가 다를 수 있다.
        // AFTER_COMMIT 비동기 경로에서만 이미지 key를 조회 URL로 바꾼다.
        realtimeRelayPublisher.publishStomp(
                "/sub/chat/rooms/" + event.roomId(), resolveImageUrls(event.payload()));
    }

    // 채팅방 종료 통지 — 남아있는 구독자가 즉시 방을 닫도록 별도 채널로 전송.
    // 종료 후에는 신규 SUBSCRIBE가 차단되지만 이미 열려 있는 세션은 끊기지 않으므로 필요하다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomClosed(ChatRoomClosedEvent event) {
        realtimeRelayPublisher.publishStomp(
                "/sub/chat/rooms/" + event.roomId() + "/closed",
                ChatRoomClosedResponseDto.of(
                        event.roomId(), event.closedByAccountId(), event.closedAt()));
    }

    private ChatMessageResponseDto resolveImageUrls(ChatMessageResponseDto payload) {
        return payload.withResolvedImageUrls(
                resolveImageUrlOrNull(payload.getSenderProfileImageUrl()),
                resolveImageUrlOrNull(payload.getImageUrl()));
    }

    private String resolveImageUrlOrNull(String objectKey) {
        if (!fileStorageService.isFinalObjectKey(objectKey)) {
            return objectKey;
        }
        try {
            return fileStorageService.resolveImageUrl(objectKey);
        } catch (RuntimeException exception) {
            log.warn("채팅 브로드캐스트 이미지 URL 발급 실패: key={}", objectKey, exception);
            return null;
        }
    }
}
