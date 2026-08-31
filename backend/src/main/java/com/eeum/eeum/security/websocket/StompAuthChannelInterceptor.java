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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 허용 destination 화이트리스트.
     *
     * <p>브로커가 {@code enableSimpleBroker("/sub")}이고 Spring의 구독 매칭은 AntPathMatcher다.
     * 그래서 {@code /sub/chat/rooms/**}를 구독하면 <b>모든 방의 메시지를 받는다</b>.
     * 접두사만 보고 형식이 안 맞으면 통과시키는 방식은 이 구독을 검증 없이 흘려보낸다 —
     * 반드시 전체 일치로 판정하고, 목록에 없는 destination은 거부해야 한다.
     *
     * <p>SEND도 같은 이유로 {@code /pub}만 허용한다. {@code /sub}는 브로커 destination이라
     * 클라이언트가 직접 SEND하면 브로커가 그대로 구독자에게 중계한다(위조 메시지 주입).
     *
     * <p>새 destination을 추가하면 이 패턴도 함께 넓혀야 한다. 넓히지 않으면 조용히 실패하는 대신
     * 거부되므로 누락을 바로 알 수 있다.
     */
    private static final Pattern CHAT_ROOM_SUBSCRIBE_DESTINATION =
            Pattern.compile("/sub/chat/rooms/(\\d{1,18})(?:/(?:read|typing|closed))?");

    private static final Pattern CHAT_ROOM_SEND_DESTINATION =
            Pattern.compile("/pub/chat/rooms/(\\d{1,18})/(?:messages|messages/image|read|typing)");

    // @SendToUser("/sub/errors") 수신용. UserDestination이 세션별로 분리하므로 방 검증 대상이 아니다.
    private static final String USER_ERROR_DESTINATION = "/user/sub/errors";

    private final JwtProvider jwtProvider;
    private final TokenService tokenService;
    private final ChatAccessHelper chatAccessHelper;
    private final AccountWriteGuard accountWriteGuard;
    private final WebSocketSessionRegistry sessionRegistry;

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
        //
        // 세대까지 보는 이유는 상태만으로 부족하기 때문이다. 비밀번호 재설정·사장 승인은
        // 계정을 ACTIVE로 남기므로, 상태만 보면 회수된 토큰으로 다시 연결할 수 있다.
        Long tokenVersion;
        try {
            tokenVersion = accountWriteGuard.assertUsableTokenWithoutLock(
                    accountId, jwtProvider.getTokenVersion(token));
        } catch (BusinessException e) {
            log.warn("WebSocket 연결 거부: accountId={}, reason={}", accountId, e.getMessage());
            throw new MessageDeliveryException("WebSocket 인증 실패: " + e.getMessage());
        }

        accessor.setUser(new StompPrincipal(accountId));
        // 제재·탈퇴 시 이 연결을 찾아 끊을 수 있도록 계정에 묶는다.
        // 세션 자체는 HTTP 업그레이드 때 이미 등록돼 있고, 여기서 주인만 붙인다.
        // 세대를 함께 기록해 둔다 — 주기적 대조가 이 값과 DB를 비교해 회수된 연결을 끊는다.
        sessionRegistry.bindAccount(accessor.getSessionId(), accountId, tokenVersion);
        log.debug("WebSocket 인증 성공: accountId={}", accountId);
    }

    // SUBSCRIBE: 허용 destination인지 먼저 판정하고, 채팅방 구독이면 참여자를 검증한다
    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();

        if (USER_ERROR_DESTINATION.equals(destination)) {
            return; // 본인 세션 전용 오류 채널
        }

        Long roomId = matchRoomId(CHAT_ROOM_SUBSCRIBE_DESTINATION, destination);
        if (roomId == null) {
            // 와일드카드(/sub/chat/rooms/**)나 알 수 없는 경로. 통과시키면 브로커가 매칭해버린다.
            log.warn("허용되지 않은 구독 destination 거부: {}", destination);
            throw new MessageDeliveryException("허용되지 않은 구독 destination입니다.");
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

    // SEND: 허용 destination인지 먼저 판정한다.
    // /sub으로 직접 SEND하면 브로커가 구독자에게 그대로 중계하므로 /pub 외에는 전부 거부한다.
    private void handleSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();

        Long roomId = matchRoomId(CHAT_ROOM_SEND_DESTINATION, destination);
        if (roomId == null) {
            log.warn("허용되지 않은 발행 destination 거부: {}", destination);
            throw new MessageDeliveryException("허용되지 않은 발행 destination입니다.");
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

    /**
     * destination이 패턴과 <b>전체 일치</b>할 때만 roomId를 돌려준다. 아니면 null.
     *
     * <p>부분 일치나 접두사 검사로 바꾸면 안 된다 — 뒤에 무엇이 붙든 통과하게 되고,
     * 그게 이 클래스가 막으려는 와일드카드 구독이다.
     */
    private Long matchRoomId(Pattern pattern, String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(destination);
        if (!matcher.matches()) {
            return null;
        }
        return Long.parseLong(matcher.group(1));
    }
}
