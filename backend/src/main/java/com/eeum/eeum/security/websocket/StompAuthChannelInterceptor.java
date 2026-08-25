package com.eeum.eeum.security.websocket;

import com.eeum.eeum.application.account.service.AccountWriteGuard;
import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.application.chat.helper.ChatAccessHelper;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CHAT_ROOM_SUBSCRIBE_PREFIX = "/sub/chat/rooms/";
    private static final String CHAT_ROOM_SEND_PREFIX = "/pub/chat/rooms/";

    private final JwtProvider jwtProvider;
    private final TokenService tokenService;
    private final ChatAccessHelper chatAccessHelper;
    private final AccountWriteGuard accountWriteGuard;

    // 사용자 검증
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            handleSubscribe(accessor);
        } else if (StompCommand.SEND.equals(command)) {
            handleSend(accessor);
        }

        return message;
    }

    // CONNECT: JWT 검증 + 계정 상태 확인 후 Principal 설정
    private void handleConnect(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor.getFirstNativeHeader("Authorization"));
        Long accountId = jwtProvider.getAccountId(token);

        // 토큰이 유효하다고 계정이 쓸 수 있는 상태인 것은 아니다.
        // 정지·탈퇴는 발급된 Access Token을 무효화하지 않으므로 남은 수명(30분) 동안 연결이 열린다.
        // REST는 JwtAuthenticationFilter가 요청마다 계정을 다시 읽지만, /ws는 그 필터를 건너뛴다.
        try {
            accountWriteGuard.assertUsableWithoutLock(accountId);
        } catch (BusinessException e) {
            log.warn("WebSocket 연결 거부: accountId={}, reason={}", accountId, e.getMessage());
            throw new MessageDeliveryException("WebSocket 인증 실패: " + e.getMessage());
        }

        accessor.setUser(new StompPrincipal(accountId));
        log.debug("WebSocket 인증 성공: accountId={}", accountId);
    }

    // SUBSCRIBE: /sub/chat/rooms/{roomId} 구독 시 참여자 검증
    private void handleSubscribe(StompHeaderAccessor accessor) {
        Long roomId = extractSubscribeRoomId(accessor.getDestination());
        if (roomId == null) {
            return; // 채팅방 외 destination은 검증 대상 아님
        }

        Long accountId = resolveAccountId(accessor);
        try {
            // 종료된 방은 참여자 레코드가 남아 있어도 구독을 허용하지 않는다
            chatAccessHelper.verifyActiveRoomParticipant(accountId, roomId);
            log.debug("채팅방 구독 허용: accountId={}, roomId={}", accountId, roomId);
        } catch (BusinessException e) {
            log.warn("채팅방 구독 거부: accountId={}, roomId={}", accountId, roomId);
            throw new MessageDeliveryException(
                    "채팅방 구독 권한 없음: roomId=" + roomId + " (" + e.getMessage() + ")");
        }
    }

    // SEND: /pub/chat/rooms/{roomId}/... 발행 시 참여자 검증 (서비스 레이어 검증의 앞단 방어)
    private void handleSend(StompHeaderAccessor accessor) {
        Long roomId = extractSendRoomId(accessor.getDestination());
        if (roomId == null) {
            return;
        }

        Long accountId = resolveAccountId(accessor);
        try {
            chatAccessHelper.verifyActiveRoomParticipant(accountId, roomId);
        } catch (BusinessException e) {
            log.warn("채팅방 발행 거부: accountId={}, roomId={}", accountId, roomId);
            throw new MessageDeliveryException(
                    "채팅방 발행 권한 없음: roomId=" + roomId + " (" + e.getMessage() + ")");
        }
    }

    // Authorization 헤더에서 토큰 추출 및 검증
    private String resolveToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException("WebSocket 인증 실패: Authorization 헤더가 없습니다.");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (!jwtProvider.isValid(token) || !jwtProvider.isAccessToken(token)) {
            throw new MessageDeliveryException("WebSocket 인증 실패: 유효하지 않은 Access Token입니다.");
        }

        if (tokenService.isBlacklisted(token)) {
            throw new MessageDeliveryException("WebSocket 인증 실패: 로그아웃된 토큰입니다.");
        }

        return token;
    }

    // SUBSCRIBE 시 Principal에서 accountId 추출
    private Long resolveAccountId(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("WebSocket 인증 실패: 인증되지 않은 연결입니다.");
        }
        return Long.parseLong(accessor.getUser().getName());
    }

    // /sub/chat/rooms/{roomId} — suffix 없는 정확한 형식만 허용 (읽기/타이핑 sub-path 제외)
    private Long extractSubscribeRoomId(String destination) {
        if (destination == null || !destination.startsWith(CHAT_ROOM_SUBSCRIBE_PREFIX)) {
            return null;
        }
        String suffix = destination.substring(CHAT_ROOM_SUBSCRIBE_PREFIX.length());
        // /sub/chat/rooms/{roomId}/typing 등 서브 경로는 상위 roomId 구독으로 허용
        int slashIdx = suffix.indexOf('/');
        String roomIdStr = slashIdx >= 0 ? suffix.substring(0, slashIdx) : suffix;
        try {
            return Long.parseLong(roomIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // /pub/chat/rooms/{roomId}/... — roomId 뒤에 action suffix가 붙는 SEND 경로
    private Long extractSendRoomId(String destination) {
        if (destination == null || !destination.startsWith(CHAT_ROOM_SEND_PREFIX)) {
            return null;
        }
        String suffix = destination.substring(CHAT_ROOM_SEND_PREFIX.length());
        int slashIdx = suffix.indexOf('/');
        String roomIdStr = slashIdx >= 0 ? suffix.substring(0, slashIdx) : suffix;
        try {
            return Long.parseLong(roomIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
