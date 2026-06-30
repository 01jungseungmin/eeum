package com.eeum.eeum.api.chat;

import com.eeum.eeum.application.chat.dto.request.ChatImageMessageSendRequestDto;
import com.eeum.eeum.application.chat.dto.request.ChatMessageSendRequestDto;
import com.eeum.eeum.application.chat.dto.request.ChatTypingRequestDto;
import com.eeum.eeum.application.chat.dto.response.ChatReadResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatTypingResponseDto;
import com.eeum.eeum.application.chat.service.ChatMessageService;
import com.eeum.eeum.application.chat.service.ChatRoomService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.security.websocket.StompPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @InjectMocks
    private ChatWebSocketController chatWebSocketController;

    @Mock private ChatMessageService chatMessageService;
    @Mock private ChatRoomService chatRoomService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    // ===================== 픽스처 헬퍼 =====================

    private StompPrincipal stompPrincipal(Long accountId) {
        return new StompPrincipal(accountId);
    }

    private ChatMessageSendRequestDto createTextRequest(String content) {
        ChatMessageSendRequestDto dto = new ChatMessageSendRequestDto();
        ReflectionTestUtils.setField(dto, "content", content);
        return dto;
    }

    private ChatImageMessageSendRequestDto createImageRequest(String imageUrl) {
        ChatImageMessageSendRequestDto dto = new ChatImageMessageSendRequestDto();
        ReflectionTestUtils.setField(dto, "imageUrl", imageUrl);
        return dto;
    }

    private ChatTypingRequestDto createTypingRequest(boolean typing) {
        ChatTypingRequestDto dto = new ChatTypingRequestDto();
        ReflectionTestUtils.setField(dto, "typing", typing);
        return dto;
    }

    // ===================== sendMessage =====================

    @Test
    void 텍스트_메시지_StompPrincipal로_accountId_추출_서비스_위임() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        ChatMessageSendRequestDto request = createTextRequest("안녕하세요");

        // When
        chatWebSocketController.sendMessage(roomId, request, stompPrincipal(accountId));

        // Then — 브로드캐스트는 ChatBroadcastEventListener가 AFTER_COMMIT 후 처리 (컨트롤러 직접 전송 없음)
        verify(chatMessageService).sendMessage(eq(accountId), eq(roomId), eq(request));
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void 텍스트_메시지_일반_Principal_getName_으로_accountId_추출() {
        // Given
        Long accountId = 2L;
        Long roomId = 10L;
        ChatMessageSendRequestDto request = createTextRequest("안녕");
        Principal regularPrincipal = mock(Principal.class);
        when(regularPrincipal.getName()).thenReturn(String.valueOf(accountId));

        // When
        chatWebSocketController.sendMessage(roomId, request, regularPrincipal);

        // Then
        verify(chatMessageService).sendMessage(eq(accountId), eq(roomId), eq(request));
        verifyNoInteractions(messagingTemplate);
    }

    // ===================== sendImageMessage =====================

    @Test
    void 이미지_메시지_서비스에_위임_messagingTemplate_직접_호출_없음() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        ChatImageMessageSendRequestDto request = createImageRequest("https://cdn.example.com/img.jpg");

        // When
        chatWebSocketController.sendImageMessage(roomId, request, stompPrincipal(accountId));

        // Then — 브로드캐스트는 ChatBroadcastEventListener가 처리
        verify(chatMessageService).sendImageMessage(eq(accountId), eq(roomId), eq(request));
        verifyNoInteractions(messagingTemplate);
    }

    // ===================== markAsRead =====================

    @Test
    void 읽음처리_결과를_올바른_destination으로_전송() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        ChatReadResponseDto response = ChatReadResponseDto.builder()
                .roomId(roomId)
                .accountId(accountId)
                .lastReadMessageId(200L)
                .readAt(LocalDateTime.now())
                .build();

        when(chatRoomService.markRoomAsReadWithResult(accountId, roomId)).thenReturn(response);

        // When
        chatWebSocketController.markAsRead(roomId, stompPrincipal(accountId));

        // Then
        verify(messagingTemplate).convertAndSend("/sub/chat/rooms/" + roomId + "/read", response);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void 읽음처리_마지막메시지없으면_lastReadMessageId_null_포함_전송() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        ChatReadResponseDto response = ChatReadResponseDto.builder()
                .roomId(roomId)
                .accountId(accountId)
                .lastReadMessageId(null)
                .readAt(LocalDateTime.now())
                .build();

        when(chatRoomService.markRoomAsReadWithResult(accountId, roomId)).thenReturn(response);

        // When
        chatWebSocketController.markAsRead(roomId, stompPrincipal(accountId));

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/sub/chat/rooms/" + roomId + "/read"), captor.capture());
        assertThat(((ChatReadResponseDto) captor.getValue()).getLastReadMessageId()).isNull();
    }

    // ===================== sendTyping =====================

    @Test
    void 타이핑중_이벤트_nickname_포함_올바른_destination_전송() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        ChatTypingRequestDto request = createTypingRequest(true);

        when(chatRoomService.verifyParticipantAndGetNickname(accountId, roomId)).thenReturn("테스터");

        // When
        chatWebSocketController.sendTyping(roomId, request, stompPrincipal(accountId));

        // Then
        ArgumentCaptor<ChatTypingResponseDto> captor = ArgumentCaptor.forClass(ChatTypingResponseDto.class);
        verify(messagingTemplate).convertAndSend(
                eq("/sub/chat/rooms/" + roomId + "/typing"), captor.capture());
        ChatTypingResponseDto captured = captor.getValue();
        assertThat(captured.getRoomId()).isEqualTo(roomId);
        assertThat(captured.getAccountId()).isEqualTo(accountId);
        assertThat(captured.getNickname()).isEqualTo("테스터");
        assertThat(captured.isTyping()).isTrue();
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void 타이핑_종료_이벤트_typing_false_로_전송() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        ChatTypingRequestDto request = createTypingRequest(false);

        when(chatRoomService.verifyParticipantAndGetNickname(accountId, roomId)).thenReturn("테스터");

        // When
        chatWebSocketController.sendTyping(roomId, request, stompPrincipal(accountId));

        // Then
        ArgumentCaptor<ChatTypingResponseDto> captor = ArgumentCaptor.forClass(ChatTypingResponseDto.class);
        verify(messagingTemplate).convertAndSend(
                eq("/sub/chat/rooms/" + roomId + "/typing"), captor.capture());
        assertThat(captor.getValue().isTyping()).isFalse();
    }

    @Test
    void 타이핑_이벤트_비참여자이면_서비스에서_예외발생() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        ChatTypingRequestDto request = createTypingRequest(true);

        when(chatRoomService.verifyParticipantAndGetNickname(accountId, roomId))
                .thenThrow(new BusinessException(ErrorCode.CHAT_NOT_PARTICIPANT));

        // When — 예외는 @MessageExceptionHandler가 처리
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> chatWebSocketController.sendTyping(roomId, request, stompPrincipal(accountId)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_NOT_PARTICIPANT);

        verifyNoInteractions(messagingTemplate);
    }

    // ===================== @MessageExceptionHandler =====================

    @Test
    void 비즈니스_예외_핸들러_실패응답_에러코드_포함() {
        // Given
        BusinessException e = new BusinessException(ErrorCode.CHAT_NOT_PARTICIPANT);

        // When
        ApiResponse<Void> response = chatWebSocketController.handleBusinessException(e);

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().getCode()).isEqualTo(ErrorCode.CHAT_NOT_PARTICIPANT.getCode());
    }

    @Test
    void 일반_예외_핸들러_INTERNAL_ERROR_코드_반환() {
        // Given
        Exception e = new RuntimeException("예기치 못한 오류");

        // When
        ApiResponse<Void> response = chatWebSocketController.handleException(e);

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().getCode()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void 비즈니스_예외_핸들러_서비스가_throw한_예외를_구조화된_응답으로_변환() {
        // Given — 서비스 예외 종류별 핸들러 동작 검증
        BusinessException roomInactive = new BusinessException(ErrorCode.CHAT_ROOM_INACTIVE);
        BusinessException duplicateMsg = new BusinessException(ErrorCode.CHAT_MESSAGE_DUPLICATE);

        // When
        ApiResponse<Void> r1 = chatWebSocketController.handleBusinessException(roomInactive);
        ApiResponse<Void> r2 = chatWebSocketController.handleBusinessException(duplicateMsg);

        // Then
        assertThat(r1.getError().getCode()).isEqualTo(ErrorCode.CHAT_ROOM_INACTIVE.getCode());
        assertThat(r2.getError().getCode()).isEqualTo(ErrorCode.CHAT_MESSAGE_DUPLICATE.getCode());
    }
}
