package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.common.util.RedisUtil;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 일회용 토큰(재인증·비밀번호 재설정)의 원자적 소비.
 *
 * <p>Mock으로는 이 성질을 검증할 수 없다 — 조회와 삭제를 나눠 쓰는 구현도 단일 스레드에서는
 * 똑같이 통과한다. 실제 Redis에 같은 토큰으로 동시에 들어가야 드러난다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class OneTimeTokenConsumptionIntegrationTest extends IntegrationTestSupport {

    private static final int CONCURRENCY = 8;

    private final TokenService tokenService;
    private final RedisUtil redisUtil;

    // ===================== ReAuth Token =====================

    @Test
    void 같은_재인증_토큰으로_동시에_요청해도_한_번만_통과한다() throws Exception {
        // Given
        Long accountId = 90001L;
        String reAuthToken = tokenService.generateAndSaveReAuthToken(accountId, 0L);

        // When
        Result result = raceOnSameToken(() -> {
            tokenService.consumeReAuthToken(accountId, reAuthToken);
            return null;
        });

        // Then: 조회와 삭제를 나눠 쓰면 여러 스레드가 같은 저장값을 보고 모두 통과한다.
        //       비밀번호 변경·회원 탈퇴가 그만큼 중복 실행된다.
        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(CONCURRENCY - 1);
        assertThat(redisUtil.get("reauth:" + accountId)).isEmpty();
    }

    @Test
    void 소비된_재인증_토큰은_다시_쓸_수_없다() {
        // Given
        Long accountId = 90002L;
        String reAuthToken = tokenService.generateAndSaveReAuthToken(accountId, 0L);
        tokenService.consumeReAuthToken(accountId, reAuthToken);

        // When & Then
        assertThatThrownBy(() -> tokenService.consumeReAuthToken(accountId, reAuthToken))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_REAUTH_TOKEN);
    }

    // ===================== Password Reset Token =====================

    @Test
    void 같은_비밀번호_재설정_토큰으로_동시에_요청해도_한_번만_통과한다() throws Exception {
        // Given
        Long accountId = 90003L;
        String resetToken = tokenService.generateAndSavePasswordResetToken(accountId, 0L);

        // When
        Result result = raceOnSameToken(() -> tokenService.consumePasswordResetToken(resetToken));

        // Then
        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(CONCURRENCY - 1);
        assertThat(redisUtil.get("password-reset:" + accountId)).isEmpty();
    }

    @Test
    void 소비된_비밀번호_재설정_토큰은_다시_쓸_수_없다() {
        // Given
        Long accountId = 90004L;
        String resetToken = tokenService.generateAndSavePasswordResetToken(accountId, 0L);
        assertThat(tokenService.consumePasswordResetToken(resetToken)).isEqualTo(accountId);

        // When & Then
        assertThatThrownBy(() -> tokenService.consumePasswordResetToken(resetToken))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_RESET_TOKEN);
    }

    // ===================== 경쟁 실행 헬퍼 =====================

    private record Result(int success, int failure) { }

    /** 같은 토큰으로 CONCURRENCY개 스레드를 동시에 출발시켜 성공/실패 수를 센다. */
    private Result raceOnSameToken(Callable<?> consume) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < CONCURRENCY; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        consume.call();
                        success.incrementAndGet();
                    } catch (BusinessException expected) {
                        failure.incrementAndGet();
                    }
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        return new Result(success.get(), failure.get());
    }
}
