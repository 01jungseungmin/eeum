package com.eeum.eeum.application.auth.listener;

import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountTokenCleanupEventListener {

    // reissue의 lease(5초)보다 넉넉히 잡는다 — 진행 중인 재발급이 끝나기를 기다리기 위함이다.
    private static final Duration LOCK_LEASE = Duration.ofSeconds(5);
    private static final Duration LOCK_MAX_WAIT = Duration.ofSeconds(10);

    private final TokenService tokenService;
    private final RedisLockService redisLockService;
    private final StringRedisTemplate redisTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountTokenCleanup(AccountTokenCleanupEvent event) {
        try {
            if (event.accountId() != null) {
                cleanupAccountTokens(event);
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
     * 재발급과 같은 락으로 직렬화한다.
     *
     * <p>직렬화하지 않으면 이런 순서가 가능하다 — 재발급이 계정 상태를 ACTIVE로 확인 →
     * 관리자가 정지시키고 이 리스너가 토큰 삭제 → 재발급이 <b>새 Refresh Token을 저장</b>.
     * 제재했는데 토큰이 되살아나고, 나중에 계정을 재활성화하면 그 세션이 그대로 살아난다.
     *
     * <p>{@code executeWithLock}(빠른 실패)을 쓰지 않는다. 진행 중인 재발급 때문에 정리를
     * 건너뛰면 그것이 곧 구멍이다. 기다렸다가 재발급 뒤에 지워야 한다.
     */
    private void cleanupAccountTokens(AccountTokenCleanupEvent event) {
        Long accountId = event.accountId();
        try {
            redisLockService.executeWithLockWaiting(
                    LockKeys.reissue(accountId), LOCK_LEASE, LOCK_MAX_WAIT,
                    () -> deleteTokens(event));
        } catch (BusinessException lockFailure) {
            // 락을 못 얻었다고 정리를 포기하면 제재된 계정의 토큰이 살아남는다.
            // 지우지 않는 쪽이 더 위험하므로 락 없이 진행한다 — 수정 전과 같은 수준이고,
            // 여기까지 오는 것 자체가 비정상이라 크게 남긴다.
            log.error("토큰 정리 락 획득 실패({}초) — 락 없이 정리한다: accountId={}",
                    LOCK_MAX_WAIT.toSeconds(), accountId, lockFailure);
            deleteTokens(event);
        }
    }

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
