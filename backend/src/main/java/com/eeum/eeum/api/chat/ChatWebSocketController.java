package com.eeum.eeum.api.chat;

import com.eeum.eeum.application.chat.dto.request.ChatImageMessageSendRequestDto;
import com.eeum.eeum.application.chat.dto.request.ChatMessageSendRequestDto;
import com.eeum.eeum.application.chat.dto.request.ChatTypingRequestDto;
import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatReadResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatTypingResponseDto;
import com.eeum.eeum.application.chat.service.ChatMessageService;
import com.eeum.eeum.application.chat.service.ChatRoomService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.security.websocket.StompPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatMessageService chatMessageService;
    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;

    // 클라이언트 발행: /pub/chat/rooms/{roomId}/messages
    // 서버 브로드캐스트: /sub/chat/rooms/{roomId}
    // DB 저장, unread 증가
    // FCM 이벤트 발행은 기존 ChatMessageService.sendMessage() 그대로 위임
    @MessageMapping("/chat/rooms/{roomId}/messages")
    public void sendMessage(
            @DestinationVariable Long roomId,
            ChatMessageSendRequestDto request,
            Principal principal
    ) {
        Long accountId = resolveAccountId(principal);

        chatMessageService.sendMessage(accountId, roomId, request);

        log.debug("WebSocket 메시지 브로드캐스트: roomId={}, accountId={}",
                roomId, accountId);
    }

    // 클라이언트 발행: /pub/chat/rooms/{roomId}/messages/image
    // 서버 브로드캐스트: /sub/chat/rooms/{roomId} (ChatBroadcastEventListener가 AFTER_COMMIT에 전송)
    // 컨트롤러에서 직접 broadcast하지 않음 — sendImageMessage가 ChatMessageBroadcastEvent 발행
    @MessageMapping("/chat/rooms/{roomId}/messages/image")
    public void sendImageMessage(
            @DestinationVariable Long roomId,
            ChatImageMessageSendRequestDto request,
            Principal principal
    ) {
        Long accountId = resolveAccountId(principal);
        chatMessageService.sendImageMessage(accountId, roomId, request);
        log.debug("WebSocket 이미지 메시지 처리: roomId={}, accountId={}", roomId, accountId);
    }

    // 클라이언트 발행: /pub/chat/rooms/{roomId}/read
    // 서버 브로드캐스트: /sub/chat/rooms/{roomId}/read
    // 단일 트랜잭션 안에서 lastReadTime 갱신 + 최신 messageId 조회 → 두 트랜잭션 분리로 인한 레이스 방지
    @MessageMapping("/chat/rooms/{roomId}/read")
    public void markAsRead(
            @DestinationVariable Long roomId,
            Principal principal
    ) {
        Long accountId = resolveAccountId(principal);
        ChatReadResponseDto response = chatRoomService.markRoomAsReadWithResult(accountId, roomId);
        messagingTemplate.convertAndSend("/sub/chat/rooms/" + roomId + "/read", response);
        log.debug("WebSocket 읽음 처리 브로드캐스트: roomId={}, accountId={}, lastReadMessageId={}",
                roomId, accountId, response.getLastReadMessageId());
    }

    // 클라이언트 발행: /pub/chat/rooms/{roomId}/typing
    // 서버 브로드캐스트: /sub/chat/rooms/{roomId}/typing
    // DB 저장 없음 — 실시간 상태 전달 전용
    @MessageMapping("/chat/rooms/{roomId}/typing")
    public void sendTyping(
            @DestinationVariable Long roomId,
            ChatTypingRequestDto request,
            Principal principal
    ) {
        Long accountId = resolveAccountId(principal);
        String nickname = chatRoomService.verifyParticipantAndGetNickname(accountId, roomId);

        ChatTypingResponseDto response = ChatTypingResponseDto.builder()
                .roomId(roomId)
                .accountId(accountId)
                .nickname(nickname)
                .typing(request.isTyping())
                .build();

        messagingTemplate.convertAndSend("/sub/chat/rooms/" + roomId + "/typing", response);
        log.debug("WebSocket 타이핑 이벤트 브로드캐스트: roomId={}, accountId={}, typing={}",
                roomId, accountId, request.isTyping());
    }

    // BusinessException(비즈니스 오류) — 클라이언트에 구조화된 에러 전달
    // REST GlobalExceptionHandler는 WebSocket 프레임을 처리하지 못하므로 별도 핸들러 필요
    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/sub/errors")
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("WebSocket 비즈니스 예외: {}", e.getMessage());
        return ApiResponse.fail(e.getErrorCode());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/sub/errors")
    public ApiResponse<Void> handleException(Exception e) {
        log.error("WebSocket 예외", e);
        return ApiResponse.fail("INTERNAL_ERROR", "서버 오류가 발생했습니다.");
    }

    private Long resolveAccountId(Principal principal) {
        if (principal instanceof StompPrincipal stompPrincipal) {
            return stompPrincipal.accountId();
        }
        return Long.parseLong(principal.getName());
    }
}
