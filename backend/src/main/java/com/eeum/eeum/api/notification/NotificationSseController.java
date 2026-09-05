package com.eeum.eeum.api.notification;

import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.common.util.SecurityUtil;
import com.eeum.eeum.security.jwt.JwtProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 실시간 알림 구독 — 실시간 트래픽을 받는 인스턴스에서만 켠다.
 *
 * <p>SseEmitter는 그 커넥션을 받은 JVM 메모리에만 존재한다. 인스턴스를 늘리면
 * A 서버에서 발생한 알림이 B 서버에 붙은 구독자에게 가지 않으므로,
 * SSE 커넥션을 한 인스턴스로 모으고(LB 경로 라우팅) API 전용 인스턴스에서는 끈다.
 *
 * <p>{@code eeum.realtime.enabled=false}로 끈다. 기본값은 켬이라 단일 인스턴스 동작은 그대로다.
 * 끄면 이 경로는 404가 되어, 잘못 라우팅된 요청이 영영 이벤트가 오지 않는 커넥션을
 * 붙들고 있는 상황을 막는다.
 *
 * <p>알림 목록·읽음 처리 같은 일반 API는 {@link NotificationController}에 있고 모든 인스턴스에서 뜬다.
 */
@Tag(name = "16. Notification", description = "알림 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@ConditionalOnProperty(name = "eeum.realtime.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationSseController {

    private final NotificationService notificationService;
    private final TokenService tokenService;
    private final JwtProvider jwtProvider;

    // 웹 클라이언트(사장님/관리자 대시보드)에서 한 번 연결하면 새 알림이 생길 때마다
    // 서버가 unread-count 이벤트를 push한다. 끊기면 EventSource가 자동 재연결한다.
    @Operation(
            summary = "SSE 실시간 알림 구독",
            description = "웹 사장님/관리자 대시보드용 실시간 배지 카운트 스트림. " +
                    "연결 즉시 현재 unread 카운트를 전송하고, 이후 변경될 때마다 push한다."
    )
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestHeader("Authorization") String authorizationHeader) {
        Long accountId = SecurityUtil.getCurrentAccountId();
        String accessToken = jwtProvider.resolveAccessToken(authorizationHeader);
        return notificationService.subscribe(
                accountId,
                SecurityUtil.getCurrentTokenVersion(),
                tokenService.accessTokenFingerprint(accessToken));
    }
}
