package com.eeum.eeum.security.websocket;

import com.eeum.eeum.application.account.service.AccountWriteGuard;
import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.application.chat.helper.ChatAccessHelper;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebSocket 인증 인터셉터.
 *
 * <p>여기가 /ws의 유일한 인가 지점이다 — JwtAuthenticationFilter는 /ws를 shouldNotFilter로
 * 건너뛰므로 REST에서 요청마다 도는 계정 상태 재확인이 이 경로에는 없다.
 */
@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    private static final String TOKEN = "valid-access-token";
    private static final Long ACCOUNT_ID = 1L;

    @InjectMocks
    private StompAuthChannelInterceptor interceptor;

    @Mock private JwtProvider jwtProvider;
    @Mock private TokenService tokenService;
    @Mock private ChatAccessHelper chatAccessHelper;
    @Mock private AccountWriteGuard accountWriteGuard;
    @Mock private WebSocketSessionRegistry sessionRegistry;
    @Mock private MessageChannel channel;

    // ===================== 픽스처 헬퍼 =====================

    private StompHeaderAccessor connectAccessor() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", "Bearer " + TOKEN);
        return accessor;
    }

    private StompHeaderAccessor sendAccessor(Long roomId) {
        return sendTo("/pub/chat/rooms/" + roomId + "/messages");
    }

    private StompHeaderAccessor sendTo(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination(destination);
        accessor.setUser(new StompPrincipal(ACCOUNT_ID));
        return accessor;
    }

    private StompHeaderAccessor subscribeTo(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination(destination);
        accessor.setUser(new StompPrincipal(ACCOUNT_ID));
        return accessor;
    }

    private Message<byte[]> toMessage(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void givenValidToken() {
        when(jwtProvider.isValid(TOKEN)).thenReturn(true);
        when(jwtProvider.isAccessToken(TOKEN)).thenReturn(true);
        when(tokenService.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtProvider.getAccountId(TOKEN)).thenReturn(ACCOUNT_ID);
    }

    private void givenTokenVersion(Long version) {
        when(jwtProvider.getTokenVersion(TOKEN)).thenReturn(version);
    }

    // ===================== CONNECT =====================

    @Test
    void 활성_계정은_연결이_허용되고_Principal이_설정된다() {
        // Given
        givenValidToken();
        StompHeaderAccessor accessor = connectAccessor();

        // When
        interceptor.preSend(toMessage(accessor), channel);

        // Then
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo(String.valueOf(ACCOUNT_ID));
        verify(accountWriteGuard).assertUsableTokenWithoutLock(eq(ACCOUNT_ID), any());
        // 제재·탈퇴 시 이 연결을 찾아 끊으려면 계정에 묶여 있어야 한다
        verify(sessionRegistry).bindAccount(eq(accessor.getSessionId()), eq(ACCOUNT_ID), any());
    }

    @Test
    void 정지된_계정은_토큰이_유효해도_연결이_거부된다() {
        // Given: 정지는 이미 발급된 Access Token을 무효화하지 않는다.
        //        토큰 검증만으로 통과시키면 남은 수명(30분) 동안 채팅 연결이 열린다.
        givenValidToken();
        when(accountWriteGuard.assertUsableTokenWithoutLock(eq(ACCOUNT_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.ACCOUNT_SUSPENDED));
        StompHeaderAccessor accessor = connectAccessor();

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void 거부된_연결은_계정에_묶이지_않는다() {
        // Given: 묶이면 끊을 대상 목록에 유령 세션이 남는다
        givenValidToken();
        when(accountWriteGuard.assertUsableTokenWithoutLock(eq(ACCOUNT_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.ACCOUNT_SUSPENDED));

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(connectAccessor()), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verify(sessionRegistry, never()).bindAccount(any(), anyLong(), any());
    }

    @Test
    void 탈퇴한_계정은_토큰이_유효해도_연결이_거부된다() {
        // Given
        givenValidToken();
        when(accountWriteGuard.assertUsableTokenWithoutLock(eq(ACCOUNT_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN));
        StompHeaderAccessor accessor = connectAccessor();

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void 회수된_세대의_토큰으로는_연결할_수_없다() {
        // Given: 비밀번호 재설정·사장 승인은 계정을 ACTIVE로 남긴다.
        //        상태만 보면 회수된 토큰으로 다시 연결할 수 있다.
        givenValidToken();
        when(accountWriteGuard.assertUsableTokenWithoutLock(eq(ACCOUNT_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));
        StompHeaderAccessor accessor = connectAccessor();

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void 연결_시점의_토큰_세대를_세션에_기록한다() {
        // Given: 주기적 대조가 이 값과 DB를 비교해 회수된 연결을 찾는다
        givenValidToken();
        when(accountWriteGuard.assertUsableTokenWithoutLock(eq(ACCOUNT_ID), any())).thenReturn(7L);
        StompHeaderAccessor accessor = connectAccessor();

        // When
        interceptor.preSend(toMessage(accessor), channel);

        // Then
        verify(sessionRegistry).bindAccount(eq(accessor.getSessionId()), eq(ACCOUNT_ID), eq(7L));
    }

    @Test
    void 토큰이_유효하지_않으면_계정_상태를_조회하지_않는다() {
        // Given
        when(jwtProvider.isValid(TOKEN)).thenReturn(false);
        StompHeaderAccessor accessor = connectAccessor();

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verify(accountWriteGuard, never()).assertUsableTokenWithoutLock(anyLong(), any());
    }

    // ===================== SUBSCRIBE destination 화이트리스트 =====================
    // 브로커가 SimpleBroker(/sub) + AntPathMatcher라, 형식이 안 맞는 destination을 "검증 대상 아님"으로
    // 통과시키면 와일드카드 구독이 모든 방의 메시지를 받는다.

    @Test
    void 와일드카드_구독은_참여자_검증_없이_거부된다() {
        // Given: /sub/chat/rooms/** 는 브로커가 모든 방 메시지에 매칭시킨다
        StompHeaderAccessor accessor = subscribeTo("/sub/chat/rooms/**");

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verifyNoInteractions(chatAccessHelper);
    }

    @Test
    void 상위_와일드카드_구독도_거부된다() {
        // Given
        StompHeaderAccessor accessor = subscribeTo("/sub/**");

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verifyNoInteractions(chatAccessHelper);
    }

    @Test
    void 단일_레벨_와일드카드_구독도_거부된다() {
        // Given: AntPathMatcher는 *도 한 세그먼트에 매칭한다
        StompHeaderAccessor accessor = subscribeTo("/sub/chat/rooms/*");

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verifyNoInteractions(chatAccessHelper);
    }

    @Test
    void 채팅방_구독과_하위_경로는_모두_참여자_검증을_거친다() {
        // Given & When & Then
        for (String destination : List.of(
                "/sub/chat/rooms/10",
                "/sub/chat/rooms/10/read",
                "/sub/chat/rooms/10/typing",
                "/sub/chat/rooms/10/closed")) {
            assertThatCode(() -> interceptor.preSend(toMessage(subscribeTo(destination)), channel))
                    .as(destination)
                    .doesNotThrowAnyException();
        }
        verify(chatAccessHelper, times(4)).verifyActiveRoomParticipant(ACCOUNT_ID, 10L);
    }

    @Test
    void 개인_오류_채널_구독은_방_검증_없이_허용된다() {
        // Given: @SendToUser("/sub/errors") 수신용 — UserDestination이 세션별로 분리한다
        StompHeaderAccessor accessor = subscribeTo("/user/sub/errors");

        // When & Then
        assertThatCode(() -> interceptor.preSend(toMessage(accessor), channel))
                .doesNotThrowAnyException();
        verifyNoInteractions(chatAccessHelper);
    }

    @Test
    void 참여하지_않은_방은_구독할_수_없다() {
        // Given
        doThrow(new ForbiddenException(ErrorCode.CHAT_NOT_PARTICIPANT))
                .when(chatAccessHelper).verifyActiveRoomParticipant(ACCOUNT_ID, 10L);

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(subscribeTo("/sub/chat/rooms/10")), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    // ===================== SEND =====================

    @Test
    void 연결_이후_정지된_계정은_발행이_거부된다() {
        // Given: CONNECT 검사만으로는 이미 열린 연결을 막지 못한다.
        //        계정 상태 판정은 verifyActiveRoomParticipant가 참여자·방 상태와 함께 수행한다.
        Long roomId = 10L;
        doThrow(new BusinessException(ErrorCode.ACCOUNT_SUSPENDED))
                .when(chatAccessHelper).verifyActiveRoomParticipant(ACCOUNT_ID, roomId);

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(sendAccessor(roomId)), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void 발행은_방마다_참여자_검증을_거친다() {
        // Given
        Long roomId = 10L;

        // When & Then
        assertThatCode(() -> interceptor.preSend(toMessage(sendAccessor(roomId)), channel))
                .doesNotThrowAnyException();
        verify(chatAccessHelper).verifyActiveRoomParticipant(ACCOUNT_ID, roomId);
    }

    @Test
    void 브로커_destination으로_직접_발행할_수_없다() {
        // Given: /sub은 브로커 destination이라 SEND하면 구독자에게 그대로 중계된다.
        //        /pub 접두사만 검사하고 나머지를 통과시키면 임의의 방에 위조 메시지를 넣을 수 있다.
        StompHeaderAccessor accessor = sendTo("/sub/chat/rooms/10");

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verifyNoInteractions(chatAccessHelper);
    }

    @Test
    void 알_수_없는_발행_경로는_거부된다() {
        // Given
        StompHeaderAccessor accessor = sendTo("/pub/chat/rooms/10/unknown");

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verifyNoInteractions(chatAccessHelper);
    }

    @Test
    void 허용된_발행_경로는_모두_참여자_검증을_거친다() {
        // Given & When & Then
        for (String destination : List.of(
                "/pub/chat/rooms/10/messages",
                "/pub/chat/rooms/10/messages/image",
                "/pub/chat/rooms/10/read",
                "/pub/chat/rooms/10/typing")) {
            assertThatCode(() -> interceptor.preSend(toMessage(sendTo(destination)), channel))
                    .as(destination)
                    .doesNotThrowAnyException();
        }
        verify(chatAccessHelper, times(4)).verifyActiveRoomParticipant(ACCOUNT_ID, 10L);
    }

    @Test
    void 참여하지_않은_방으로는_발행할_수_없다() {
        // Given
        Long roomId = 10L;
        doThrow(new ForbiddenException(ErrorCode.CHAT_NOT_PARTICIPANT))
                .when(chatAccessHelper).verifyActiveRoomParticipant(ACCOUNT_ID, roomId);

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(sendAccessor(roomId)), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }
}
