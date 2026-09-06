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

/**
 * 채팅 메시지 WebSocket 브로드캐스트 리스너.
 * DB 커밋 완료 후 동기 실행(@Async 없음) — 클라이언트에 일관된 상태를 전달하기 위해 커밋 후 전송.
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
    public void onBroadcast(ChatMessageBroadcastEvent event) {
        // Redis로 중계한다 — 이 메시지를 만든 인스턴스와 구독자가 붙은 인스턴스가 다를 수 있다.
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

    // MVC ResponseBodyAdvice는 STOMP payload에 적용되지 않는다. private S3 key는 브로드캐스트 전에
    // 조회 URL로 바꿔야 참여자가 즉시 표시할 수 있다. AFTER_COMMIT이므로 DB 트랜잭션은 열려 있지 않다.
    private ChatMessageResponseDto resolveImageUrls(ChatMessageResponseDto payload) {
        boolean hasPrivateProfileImage = fileStorageService.isFinalObjectKey(payload.getSenderProfileImageUrl());
        boolean hasPrivateMessageImage = fileStorageService.isFinalObjectKey(payload.getImageUrl());
        if (!hasPrivateProfileImage && !hasPrivateMessageImage) {
            return payload;
        }

        return payload.withResolvedImageUrls(
                hasPrivateProfileImage
                        ? resolveImageUrlOrNull(payload.getSenderProfileImageUrl())
                        : payload.getSenderProfileImageUrl(),
                hasPrivateMessageImage
                        ? resolveImageUrlOrNull(payload.getImageUrl())
                        : payload.getImageUrl()
        );
    }

    private String resolveImageUrlOrNull(String objectKey) {
        try {
            return fileStorageService.resolveImageUrl(objectKey);
        } catch (RuntimeException exception) {
            // 커밋된 메시지를 되돌릴 수 없고 raw key를 보내면 private 버킷에서 사용할 수도 없다.
            // 클라이언트는 이후 REST 메시지 조회로 재동기화한다.
            log.warn("채팅 브로드캐스트 이미지 URL 발급 실패: key={}", objectKey, exception);
            return null;
        }
    }
}
