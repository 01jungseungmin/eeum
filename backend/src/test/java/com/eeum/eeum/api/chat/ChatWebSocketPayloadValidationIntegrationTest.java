package com.eeum.eeum.api.chat;

import com.eeum.eeum.security.websocket.StompPrincipal;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STOMP payload의 Bean Validation이 실제로 적용되는지 확인한다.
 *
 * <p>DTO에 {@code @NotBlank}·{@code @Size}가 선언돼 있어도 핸들러 파라미터에 {@code @Valid}가
 * 없으면 아무 검증도 일어나지 않는다. 그 상태에서도 단위 테스트는 전부 통과하므로,
 * <b>클라이언트가 실제로 어떤 오류를 받는지</b>로 판정한다.
 *
 * <p>검증이 걸리면 핸들러에 들어가기 전에 끊겨 VALIDATION_001이 가고,
 * 안 걸리면 핸들러가 실행돼 방을 찾다가 CHAT_001이 간다. 두 코드로 구분한다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class ChatWebSocketPayloadValidationIntegrationTest extends IntegrationTestSupport {

    private static final long UNKNOWN_ROOM_ID = 987654L;
    private static final long ACCOUNT_ID = 1L;

    private final SimpAnnotationMethodMessageHandler messageHandler;

    @Qualifier("brokerChannel")
    private final AbstractSubscribableChannel brokerChannel;

    private final List<String> outbound = new CopyOnWriteArrayList<>();

    private final ChannelInterceptor capture = new ChannelInterceptor() {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            Object payload = message.getPayload();
            if (payload instanceof byte[] bytes) {
                outbound.add(new String(bytes, StandardCharsets.UTF_8));
            } else {
                outbound.add(String.valueOf(payload));
            }
            return message;
        }
    };

    @AfterEach
    void removeInterceptor() {
        brokerChannel.removeInterceptor(capture);
        outbound.clear();
    }

    // ===================== 텍스트 메시지 =====================

    @Test
    void 빈_메시지는_핸들러에_도달하기_전에_검증에서_막힌다() {
        // Given
        brokerChannel.addInterceptor(capture);

        // When
        send("/pub/chat/rooms/" + UNKNOWN_ROOM_ID + "/messages", "{\"content\":\"\"}");

        // Then: 검증이 걸리지 않으면 핸들러가 실행돼 CHAT_001(존재하지 않는 채팅방)이 나간다
        assertThat(errorPayload()).contains("VALIDATION_001").contains("content");
    }

    @Test
    void 최대_길이를_넘는_메시지는_검증에서_막힌다() {
        // Given
        brokerChannel.addInterceptor(capture);
        String tooLong = "가".repeat(2001);

        // When
        send("/pub/chat/rooms/" + UNKNOWN_ROOM_ID + "/messages", "{\"content\":\"" + tooLong + "\"}");

        // Then
        assertThat(errorPayload()).contains("VALIDATION_001").contains("content");
    }

    @Test
    void 유효한_메시지는_검증을_통과해_핸들러까지_간다() {
        // Given
        brokerChannel.addInterceptor(capture);

        // When: 방이 없으므로 핸들러가 실행됐다는 증거로 CHAT_001이 나와야 한다
        send("/pub/chat/rooms/" + UNKNOWN_ROOM_ID + "/messages", "{\"content\":\"안녕하세요\"}");

        // Then
        assertThat(errorPayload()).contains("CHAT_001").doesNotContain("VALIDATION_001");
    }

    // ===================== 이미지 메시지 =====================

    @Test
    void 빈_이미지_URL은_검증에서_막힌다() {
        // Given
        brokerChannel.addInterceptor(capture);

        // When
        send("/pub/chat/rooms/" + UNKNOWN_ROOM_ID + "/messages/image", "{\"imageUrl\":\"\"}");

        // Then
        assertThat(errorPayload()).contains("VALIDATION_001").contains("imageUrl");
    }

    @Test
    void 최대_길이를_넘는_이미지_URL은_검증에서_막힌다() {
        // Given
        brokerChannel.addInterceptor(capture);
        String tooLong = "https://cdn.test/" + "a".repeat(500);

        // When
        send("/pub/chat/rooms/" + UNKNOWN_ROOM_ID + "/messages/image",
                "{\"imageUrl\":\"" + tooLong + "\"}");

        // Then
        assertThat(errorPayload()).contains("VALIDATION_001").contains("imageUrl");
    }

    // ===================== 헬퍼 =====================

    private void send(String destination, String jsonPayload) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setSessionId("validation-test-session");
        accessor.setSessionAttributes(new HashMap<>()); // SimpAttributes가 요구한다

        accessor.setUser(new StompPrincipal(ACCOUNT_ID));
        accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        accessor.setLeaveMutable(true);

        Message<byte[]> message = MessageBuilder.createMessage(
                jsonPayload.getBytes(StandardCharsets.UTF_8), accessor.getMessageHeaders());

        // 예외는 @MessageExceptionHandler로 흡수되므로 던지지 않는다 — 나간 오류 프레임으로 판정한다
        messageHandler.handleMessage(message);
    }

    private String errorPayload() {
        assertThat(outbound).as("오류 프레임이 브로커로 나가지 않았다").isNotEmpty();
        return String.join("\n", outbound);
    }
}
