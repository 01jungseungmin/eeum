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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
    @Mock private MessageChannel channel;

    // ===================== 픽스처 헬퍼 =====================

    private StompHeaderAccessor connectAccessor() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", "Bearer " + TOKEN);
        return accessor;
    }

    private StompHeaderAccessor sendAccessor(Long roomId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/pub/chat/rooms/" + roomId + "/messages");
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
        verify(accountWriteGuard).assertUsableWithoutLock(ACCOUNT_ID);
    }

    @Test
    void 정지된_계정은_토큰이_유효해도_연결이_거부된다() {
        // Given: 정지는 이미 발급된 Access Token을 무효화하지 않는다.
        //        토큰 검증만으로 통과시키면 남은 수명(30분) 동안 채팅 연결이 열린다.
        givenValidToken();
        doThrow(new BusinessException(ErrorCode.ACCOUNT_SUSPENDED))
                .when(accountWriteGuard).assertUsableWithoutLock(ACCOUNT_ID);
        StompHeaderAccessor accessor = connectAccessor();

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void 탈퇴한_계정은_토큰이_유효해도_연결이_거부된다() {
        // Given
        givenValidToken();
        doThrow(new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN))
                .when(accountWriteGuard).assertUsableWithoutLock(ACCOUNT_ID);
        StompHeaderAccessor accessor = connectAccessor();

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void 토큰이_유효하지_않으면_계정_상태를_조회하지_않는다() {
        // Given
        when(jwtProvider.isValid(TOKEN)).thenReturn(false);
        StompHeaderAccessor accessor = connectAccessor();

        // When & Then
        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verify(accountWriteGuard, never()).assertUsableWithoutLock(anyLong());
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
