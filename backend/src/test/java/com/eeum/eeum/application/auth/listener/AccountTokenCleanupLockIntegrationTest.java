package com.eeum.eeum.application.auth.listener;

import com.eeum.eeum.application.auth.service.TokenService;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.common.util.RedisUtil;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제재 토큰 정리가 재발급과 같은 락으로 직렬화되는지 검증한다.
 *
 * <p>직렬화하지 않으면 이런 순서가 가능하다 — 재발급이 계정 상태를 ACTIVE로 확인 →
 * 관리자가 정지시키고 정리가 토큰 삭제 → 재발급이 <b>새 Refresh Token을 저장</b>.
 * 제재했는데 토큰이 되살아난다.
 *
 * <p>Mock으로는 검증할 수 없다. 락 획득 자체가 검증 대상이라 실제 Redis가 필요하다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class AccountTokenCleanupLockIntegrationTest extends IntegrationTestSupport {

    private static final Long ACCOUNT_ID = 987654321L;

    private final AccountTokenCleanupEventListener listener;
    private final RedisLockService redisLockService;
    private final TokenService tokenService;
    private final RedisUtil redisUtil;

    @Test
    void 재발급이_진행_중이면_토큰_정리가_끝날_때까지_기다린다() throws Exception {
        // Given: 재발급이 락을 잡은 채 아직 새 토큰을 저장하지 않은 상태
        tokenService.saveRefreshToken(ACCOUNT_ID, "old-refresh-token");
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<?> reissue = pool.submit(() -> redisLockService.executeWithLock(
                    LockKeys.reissue(ACCOUNT_ID), Duration.ofSeconds(20),
                    () -> {
                        lockHeld.countDown();
                        awaitQuietly(releaseLock);
                        // 재발급이 마지막에 하는 일 — 새 Refresh Token 저장
                        tokenService.saveRefreshToken(ACCOUNT_ID, "new-refresh-token");
                    }));

            assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

            // When: 제재 정리가 들어온다
            Future<?> cleanup = pool.submit(() ->
                    listener.onAccountTokenCleanup(AccountTokenCleanupEvent.allTokens(ACCOUNT_ID)));

            // Then: 재발급이 끝나기 전에는 지우지 못한다 (빠른 실패였다면 여기서 이미 사라졌다)
            sleepQuietly(500);
            assertThat(redisUtil.get("refresh:" + ACCOUNT_ID))
                    .as("재발급이 락을 쥔 동안 정리가 먼저 지나갔다")
                    .isPresent();

            releaseLock.countDown();
            reissue.get(10, TimeUnit.SECONDS);
            cleanup.get(20, TimeUnit.SECONDS);

            // 재발급이 저장한 새 토큰까지 회수돼야 제재가 성립한다
            assertThat(awaitTokenDeleted())
                    .as("재발급이 저장한 토큰이 남았다 — 제재가 무력화된다")
                    .isTrue();
        } finally {
            releaseLock.countDown();
            pool.shutdownNow();
            redisUtil.delete("refresh:" + ACCOUNT_ID);
        }
    }

    @Test
    void 경합이_없으면_토큰을_바로_회수한다() {
        // Given
        tokenService.saveRefreshToken(ACCOUNT_ID, "refresh-token");

        // When
        listener.onAccountTokenCleanup(AccountTokenCleanupEvent.allTokens(ACCOUNT_ID));

        // Then
        assertThat(awaitTokenDeleted()).isTrue();
    }

    // 리스너는 @Async 프록시라 호출이 즉시 반환된다 — 실제 삭제까지 기다린다.
    private boolean awaitTokenDeleted() {
        for (int attempt = 0; attempt < 200; attempt++) {
            if (redisUtil.get("refresh:" + ACCOUNT_ID).isEmpty()) {
                return true;
            }
            sleepQuietly(50);
        }
        return false;
    }
}
