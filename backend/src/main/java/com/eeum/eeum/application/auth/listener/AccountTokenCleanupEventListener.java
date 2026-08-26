package com.eeum.eeum.application.auth.listener;

import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountTokenCleanupEventListener {

    private final TokenService tokenService;
    private final StringRedisTemplate redisTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountTokenCleanup(AccountTokenCleanupEvent event) {
        try {
            if (event.deleteRefreshToken() && event.accountId() != null) {
                tokenService.deleteRefreshToken(event.accountId());
            }
            if (event.consumeReAuthToken() && event.accountId() != null) {
                tokenService.deleteReAuthToken(event.accountId());
            }
            if (event.deletePasswordResetToken() && event.accountId() != null) {
                tokenService.deletePasswordResetToken(event.accountId());
            }
            if (event.oauthTempToken() != null) {
                redisTemplate.delete("oauth:temp:" + event.oauthTempToken());
            }
        } catch (Exception e) {
            // DB는 이미 커밋됐으므로 예외를 전파하지 않고 로그만 기록
            // TODO: 재시도 또는 보상 처리가 필요한 경우 별도 큐/스케줄러 고려
            log.error("AFTER_COMMIT Redis 토큰 정리 실패: event={}", event, e);
        }
    }
}
