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
import com.eeum.eeum.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.stream.Collectors;

/**
 * STOMP 수신 컨트롤러 — 브로커가 없는 인스턴스에서는 뜰 이유가 없다.
 */
@ConditionalOnProperty(name = "eeum.realtime.enabled", havingValue = "true", matchIfMissing = true)
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
            @Valid ChatMessageSendRequestDto request,
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
            @Valid ChatImageMessageSendRequestDto request,
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
            @Valid ChatTypingRequestDto request,
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

    /**
     * payload Bean Validation 실패 — 클라이언트 잘못이므로 서버 오류로 뭉뚱그리지 않는다.
     *
     * <p>이 핸들러가 없으면 아래 {@code Exception} 핸들러가 잡아 INTERNAL_ERROR로 응답한다.
     * 어떤 필드가 왜 틀렸는지 알 수 없어 클라이언트가 고칠 수 없다.
     * 응답 형식은 REST의 {@code GlobalExceptionHandler.handleValidationException}과 맞춘다.
     */
    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/sub/errors")
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        // payload 자체를 변환하지 못하면 bindingResult가 없다
        String errorMessage = e.getBindingResult() == null
                ? "요청 값이 올바르지 않습니다."
                : e.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .collect(Collectors.joining(", "));

        log.warn("WebSocket payload 검증 실패: {}", errorMessage);
        return ApiResponse.fail(ErrorCode.VALIDATION_INVALID_INPUT.getCode(), errorMessage);
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
