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
 * 제재 토큰 정리가 재발급 락에 묶이지 않는지 검증한다.
 *
 * <p>회수의 보장은 여기가 아니다. 발급 시점의 토큰 세대가 JWT에 박히고, 제재가 같은
 * 트랜잭션에서 세대를 올리므로 그 이전 토큰은 Redis에 남아 있어도 검증에서 걸린다.
 * Redis 삭제는 즉시성을 위한 최적화다.
 *
 * <p>그래서 여기서 확인하는 것은 "정리가 아무것도 기다리지 않는다"이다.
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
    void 재발급이_진행_중이어도_정리가_막히지_않는다() {
        // Given: 예전에는 정리가 재발급 락을 기다렸다. 그 대기는 비동기 스레드를 최대 10초 묶으면서도
        //        lease 만료·lockless fallback에서 경쟁이 다시 성립해 보장은 못 했다.
        //        지금 보장은 계정의 토큰 세대가 맡는다 — 제재와 같은 트랜잭션에서 오른다.
        tokenService.saveRefreshToken(ACCOUNT_ID, "refresh-token");
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            Future<?> holder = pool.submit(() -> redisLockService.executeWithLock(
                    LockKeys.reissue(ACCOUNT_ID), Duration.ofSeconds(20),
                    () -> awaitQuietly(releaseLock)));
            sleepQuietly(200);

            // When: 재발급 락이 잡혀 있는 동안 정리가 들어온다
            listener.onAccountTokenCleanup(AccountTokenCleanupEvent.allTokens(ACCOUNT_ID));

            // Then: 기다리지 않고 바로 지운다
            assertThat(awaitTokenDeleted())
                    .as("정리가 재발급 락을 기다렸다 — 비동기 스레드가 묶인다")
                    .isTrue();

            releaseLock.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
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
