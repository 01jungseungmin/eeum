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

/** SSE 실시간 알림 구독 — 실시간 트래픽을 받는 인스턴스에서만 켠다. */
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
