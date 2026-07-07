package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiFcmTestSendRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiFcmTestSendResponseDto;
import com.eeum.eeum.common.lock.RateLimitKeys;
import com.eeum.eeum.common.service.RateLimitService;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.infrastructure.push.PushAdapter;
import com.eeum.eeum.infrastructure.push.PushMessage;
import com.eeum.eeum.infrastructure.push.PushResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 발표용 FCM 단건 테스트 발송 — AI가 생성한 메시지가 실제 푸시로 이어짐을 시연한다.
 * 기존 PushAdapter(prod: FCM HTTP v1 / 그 외: NoOp)를 재사용하므로 별도 Firebase 초기화가 없다.
 * 운영용 다건 발송·고객 대상 조회는 2차 범위 — 여기서는 토큰 직접 입력만 지원한다.
 */
@Profile("!prod")
@Slf4j
@Service
@RequiredArgsConstructor
public class AiFcmTestService {

    private static final Duration FCM_TEST_COOLDOWN = Duration.ofSeconds(10);

    private final AiManagerSupportService supportService;
    private final PushAdapter pushAdapter;
    private final RateLimitService rateLimitService;

    // 외부 발송 호출 중 DB 커넥션을 점유하지 않도록 트랜잭션 없이 처리
    public AiFcmTestSendResponseDto sendTestPush(Long ownerId, AiFcmTestSendRequestDto request) {
        supportService.getOwnerStore(ownerId); // owner/store 검증

        String title;
        String content;
        if (request.messageId() != null) {
            // 타 사장 메시지 접근 시 AI_FORBIDDEN
            AiGeneratedMessage message = supportService.getOwnedMessage(ownerId, request.messageId());
            title = message.getTitle() != null ? message.getTitle() : "이음 AI 매니저";
            content = message.getContent();
        } else {
            title = request.title() != null && !request.title().isBlank() ? request.title() : "이음 AI 매니저";
            content = request.content();
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_INPUT, "발송할 본문이 없습니다");
        }

        // 콘텐츠 유효성 확인 이후에 Rate Limit 검사 — 유효하지 않은 요청에는 쿨다운 소진 불필요
        rateLimitService.checkCooldown(RateLimitKeys.fcmTest(ownerId), FCM_TEST_COOLDOWN, ErrorCode.AI_RATE_LIMITED);

        PushResult result = pushAdapter.send(PushMessage.builder()
                .fcmToken(request.fcmToken())
                .title(title)
                .body(content)
                .build());

        if (!result.isSuccess()) {
            // 토큰 값은 로그에 남기지 않는다
            log.warn("[AI-FCM-TEST] 테스트 푸시 발송 실패: errorCode={}", result.getErrorCode());
            return AiFcmTestSendResponseDto.ofFailure(result.getErrorCode());
        }
        return AiFcmTestSendResponseDto.ofSuccess();
    }
}
