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
            if (event.accountId() != null) {
                deleteTokens(event);
            }
            if (event.oauthTempToken() != null) {
                // 계정 범위가 아니라 재발급과 겹치지 않는다 — 잠글 이유가 없다.
                redisTemplate.delete("oauth:temp:" + event.oauthTempToken());
            }
        } catch (Exception e) {
            // DB는 이미 커밋됐으므로 예외를 전파하지 않고 로그만 기록
            // TODO: 재시도 또는 보상 처리가 필요한 경우 별도 큐/스케줄러 고려
            log.error("AFTER_COMMIT Redis 토큰 정리 실패: event={}", event, e);
        }
    }

    /**
     * Redis에 남은 토큰을 지운다 — <b>즉시성을 위한 최적화이지 회수의 보장이 아니다.</b>
     *
     * <p>보장은 두 곳이 맡는다. 재발급이 계정 행을 잠그고 읽으므로 제재와 직렬화되고
     * ({@code AuthService.reissue}), 제재와 같은 트랜잭션에서 기록한 무효화 시각이
     * 그보다 먼저 발급된 토큰을 전부 무효로 만든다({@code Account#tokenInvalidatedAt}).
     *
     * <p>그래서 여기서 락을 잡지 않는다. 예전에는 재발급 락을 기다렸다가 지웠는데,
     * 그 대기는 비동기 풀 스레드를 최대 10초 묶으면서도 lease 만료·lockless fallback에서
     * 경쟁이 다시 성립했다 — 보장은 못 하면서 비용만 냈다.
     *
     * <p>이 작업이 풀 포화로 버려져도 회수는 유효하다. Redis에 낡은 토큰이 남을 뿐이고
     * 그 토큰은 무효화 시각에 걸린다.
     */
    private void deleteTokens(AccountTokenCleanupEvent event) {
        Long accountId = event.accountId();
        if (event.deleteRefreshToken()) {
            tokenService.deleteRefreshToken(accountId);
        }
        if (event.consumeReAuthToken()) {
            tokenService.deleteReAuthToken(accountId);
        }
        if (event.deletePasswordResetToken()) {
            tokenService.deletePasswordResetToken(accountId);
        }
    }
}
