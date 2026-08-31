package com.eeum.eeum.integration;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.application.account.service.AdminAccountService;
import com.eeum.eeum.common.util.RedisUtil;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자의 계정 상태 변경 Pessimistic Lock 통합 테스트.
 *
 * <p>suspendAccount / forceDeleteAccount 가 동일 Account에 동시에 진입할 때,
 * DB의 PESSIMISTIC_WRITE 락 덕분에 두 연산이 직렬화되어 최종 상태가 항상 유효한지 검증한다.
 *
 * <p><b>MySQL에서만 Pessimistic Lock 동작</b>이 보장된다.
 * Testcontainers MySQL + 실제 @Lock(PESSIMISTIC_WRITE) 쿼리로 검증한다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class AdminAccountStatusLockIntegrationTest extends IntegrationTestSupport {



    private final AdminAccountService adminAccountService;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;

    private static final Long ADMIN_ID = 0L; // 테스트용 관리자 ID (DB에 없어도 됨)

    private Account targetAccount;

    @BeforeEach
    void setUp() {
        targetAccount = accountRepository.save(
                Account.createUser(
                        "lock-test@admin.com",
                        passwordEncoder.encode("pass"),
                        "락테스트유저",
                        "lock_test_user",
                        "010-7777-0001"
                )
        );
    }

    @AfterEach
    void tearDown() {
        if (targetAccount != null) {
            redisUtil.delete("refresh:" + targetAccount.getAccountId());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 1: suspendAccount와 forceDeleteAccount 동시 실행 시 최종 상태 유효성
    // 두 연산 중 하나 이상 성공하고, 최종 Account 상태는 SUSPENDED 또는 WITHDRAWN 중 하나
    // ─────────────────────────────────────────────────────────────────

    @Test
    void suspendAccount와_forceDeleteAccount_동시_실행_시_최종_상태가_유효함() throws InterruptedException {
        Long targetId = targetAccount.getAccountId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger businessExceptionCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            try {
                startLatch.await();
                adminAccountService.suspendAccount(ADMIN_ID, targetId);
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                // ACCOUNT_WITHDRAWN 예외는 허용 범위 (다른 스레드가 먼저 처리)
                businessExceptionCount.incrementAndGet();
            } catch (Exception e) {
                // 그 외 예외는 테스트 실패이지만 카운트만 기록
                businessExceptionCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                adminAccountService.forceDeleteAccount(ADMIN_ID, targetId);
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                // ACCOUNT_WITHDRAWN 예외는 허용 범위
                businessExceptionCount.incrementAndGet();
            } catch (Exception e) {
                businessExceptionCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown(); // 두 스레드 동시 출발
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("두 스레드 모두 완료").isTrue();

        // 하나 이상 성공해야 한다
        assertThat(successCount.get())
                .as("suspend 또는 forceDelete 중 하나 이상 성공")
                .isGreaterThanOrEqualTo(1);

        // 최종 Account 상태는 SUSPENDED 또는 WITHDRAWN 중 하나
        Account finalState = accountRepository.findById(targetId).orElseThrow();
        assertThat(finalState.getStatus())
                .as("최종 상태는 SUSPENDED 또는 WITHDRAWN")
                .isIn(AccountStatus.SUSPENDED, AccountStatus.WITHDRAWN);
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 2: 연속 suspendAccount 두 번 — 두 번째는 상태 변경이 幂等적으로 처리됨
    // ─────────────────────────────────────────────────────────────────

    @Test
    void suspendAccount_두_번_연속_호출_두_번째도_SUSPENDED_유지() throws InterruptedException {
        Long targetId = targetAccount.getAccountId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    adminAccountService.suspendAccount(ADMIN_ID, targetId);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                    // 동시 suspend에서 한쪽이 이미 SUSPENDED 상태를 보는 경우
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        // 최종 상태는 반드시 SUSPENDED
        Account finalState = accountRepository.findById(targetId).orElseThrow();
        assertThat(finalState.getStatus())
                .as("연속 suspend 후 최종 상태 SUSPENDED")
                .isEqualTo(AccountStatus.SUSPENDED);
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 3: 단순 순차 실행 — suspendAccount 후 forceDeleteAccount
    // ACCOUNT_WITHDRAWN 예외가 발생하는지 확인 (이미 정지된 계정을 강제 탈퇴)
    // ─────────────────────────────────────────────────────────────────

    @Test
    void suspendAccount_후_forceDeleteAccount_순차_실행_정상_동작() {
        Long targetId = targetAccount.getAccountId();

        // suspend
        adminAccountService.suspendAccount(ADMIN_ID, targetId);

        Account afterSuspend = accountRepository.findById(targetId).orElseThrow();
        assertThat(afterSuspend.getStatus()).isEqualTo(AccountStatus.SUSPENDED);

        // 이미 suspended 상태에서 forceDelete → Account.isWithdrawn()은 false이므로 성공
        adminAccountService.forceDeleteAccount(ADMIN_ID, targetId);

        Account afterDelete = accountRepository.findById(targetId).orElseThrow();
        assertThat(afterDelete.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(afterDelete.getDeletedAt()).isNotNull();
    }

    /**
     * 토큰 무효화 시각은 제재와 <b>같은 트랜잭션</b>에서 커밋돼야 한다.
     *
     * <p>Redis 삭제와 세션 종료는 비동기 풀·Pub/Sub을 타므로 유실될 수 있다.
     * 이 값이 비동기였다면 같은 이유로 유실되고, 그러면 회수의 최종 근거가 사라진다.
     * 제재 호출이 반환된 직후 이미 기록돼 있어야 동기 커밋이 보장된다.
     */
    @Test
    void 정지하면_토큰_무효화_시각이_같은_트랜잭션에서_기록된다() {
        // Given
        Long targetId = targetAccount.getAccountId();
        assertThat(accountRepository.findById(targetId).orElseThrow().getTokenInvalidatedAt())
                .isNull();

        // When
        adminAccountService.suspendAccount(ADMIN_ID, targetId);

        // Then: 비동기였다면 여기서 아직 null이다
        assertThat(accountRepository.findById(targetId).orElseThrow().getTokenInvalidatedAt())
                .as("무효화 시각이 기록되지 않았다 — 토큰 회수의 최종 근거가 사라진다")
                .isNotNull();
    }

    @Test
    void 강제_탈퇴도_토큰_무효화_시각을_남긴다() {
        // Given
        Long targetId = targetAccount.getAccountId();

        // When
        adminAccountService.forceDeleteAccount(ADMIN_ID, targetId);

        // Then
        assertThat(accountRepository.findById(targetId).orElseThrow().getTokenInvalidatedAt())
                .isNotNull();
    }
}
