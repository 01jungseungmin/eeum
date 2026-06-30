package com.eeum.eeum.integration;

import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Redis 분산 락(RedisLockService) 통합 테스트.
 *
 * <p>SetNX 방식의 락이 실제 Redis 환경에서 동시성을 제대로 제어하는지 검증한다.
 * - 동일 키: 두 번째 락 획득 시도 → LOCK_ACQUIRE_FAILED 예외
 * - 서로 다른 키: 독립적으로 동시 실행 가능
 */
@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class RedisLockIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("eeum")
            .withUsername("test")
            .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    private final RedisLockService redisLockService;
    private final StringRedisTemplate stringRedisTemplate;

    // ─────────────────────────────────────────────────────────────────
    // CASE 1: 동일 key - 두 번째 시도가 첫 번째 완료 전에 LOCK_ACQUIRE_FAILED
    // ─────────────────────────────────────────────────────────────────

    @Test
    void 동일_key에_두_번째_락_요청은_LOCK_ACQUIRE_FAILED() throws InterruptedException {
        String lockKey = "test:lock:concurrent:1";

        // 락 해제를 제어하기 위한 래치
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        AtomicBoolean firstSuccess = new AtomicBoolean(false);
        AtomicBoolean secondSuccess = new AtomicBoolean(false);
        AtomicReference<Throwable> secondError = new AtomicReference<>();

        // Thread 1: 락을 획득하고 releaseLock 신호를 기다림
        Thread t1 = new Thread(() -> {
            try {
                redisLockService.executeWithLock(lockKey, Duration.ofSeconds(10), () -> {
                    firstSuccess.set(true);
                    firstLockAcquired.countDown(); // 락 획득 신호
                    try {
                        releaseLock.await(5, TimeUnit.SECONDS); // 해제 신호 대기
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } finally {
                done.countDown();
            }
        });

        // Thread 2: Thread 1이 락을 잡은 이후에 동일 키로 시도
        Thread t2 = new Thread(() -> {
            try {
                firstLockAcquired.await(5, TimeUnit.SECONDS); // T1 락 획득까지 대기
                redisLockService.executeWithLock(lockKey, Duration.ofSeconds(10), () -> {
                    secondSuccess.set(true);
                });
            } catch (Exception e) {
                secondError.set(e);
            } finally {
                releaseLock.countDown(); // T1 해제 신호
                done.countDown();
            }
        });

        t1.start();
        t2.start();

        boolean completed = done.await(15, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(firstSuccess.get()).as("첫 번째 락은 성공").isTrue();
        assertThat(secondSuccess.get()).as("두 번째 락 획득은 실패해야 함").isFalse();
        assertThat(secondError.get())
                .as("두 번째 락 시도는 BusinessException(LOCK_ACQUIRE_FAILED)")
                .isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) secondError.get()).getErrorCode())
                .isEqualTo(ErrorCode.LOCK_ACQUIRE_FAILED);
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 2: 서로 다른 key - 동시에 독립적으로 실행 가능
    // ─────────────────────────────────────────────────────────────────

    @Test
    void 다른_key는_동시에_독립적으로_락_획득_가능() throws InterruptedException {
        String lockKey1 = "test:lock:independent:1";
        String lockKey2 = "test:lock:independent:2";

        CountDownLatch bothAcquired = new CountDownLatch(2);
        CountDownLatch done = new CountDownLatch(2);

        AtomicBoolean success1 = new AtomicBoolean(false);
        AtomicBoolean success2 = new AtomicBoolean(false);

        Thread t1 = new Thread(() -> {
            try {
                redisLockService.executeWithLock(lockKey1, Duration.ofSeconds(5), () -> {
                    success1.set(true);
                    bothAcquired.countDown();
                    try {
                        Thread.sleep(200); // 짧게 슬립 (독립성 확인)
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } finally {
                done.countDown();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                redisLockService.executeWithLock(lockKey2, Duration.ofSeconds(5), () -> {
                    success2.set(true);
                    bothAcquired.countDown();
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } finally {
                done.countDown();
            }
        });

        t1.start();
        t2.start();

        boolean bothGotLock = bothAcquired.await(5, TimeUnit.SECONDS);
        boolean completed = done.await(10, TimeUnit.SECONDS);

        assertThat(bothGotLock).as("두 스레드 모두 락을 동시에 획득해야 함").isTrue();
        assertThat(completed).isTrue();
        assertThat(success1.get()).as("lockKey1 작업 성공").isTrue();
        assertThat(success2.get()).as("lockKey2 작업 성공").isTrue();
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 3: 락 해제 후 동일 key 재획득 가능
    // ─────────────────────────────────────────────────────────────────

    @Test
    void 락_해제_후_동일_key_재획득_가능() {
        String lockKey = "test:lock:reacquire:1";

        AtomicBoolean firstResult = new AtomicBoolean(false);
        AtomicBoolean secondResult = new AtomicBoolean(false);

        // 첫 번째 락: 정상 실행 후 해제
        redisLockService.executeWithLock(lockKey, Duration.ofSeconds(5), () -> {
            firstResult.set(true);
        });

        // 첫 번째 락이 해제된 뒤 동일 키로 재획득 → 성공해야 함
        redisLockService.executeWithLock(lockKey, Duration.ofSeconds(5), () -> {
            secondResult.set(true);
        });

        assertThat(firstResult.get()).as("첫 번째 작업 성공").isTrue();
        assertThat(secondResult.get()).as("락 해제 후 재획득 성공").isTrue();
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 4: 작업 중 예외 발생 시 락이 자동 해제됨 (finally 보장)
    // ─────────────────────────────────────────────────────────────────

    @Test
    void 작업_중_예외_발생_시_락이_자동_해제되어_재획득_가능() {
        String lockKey = "test:lock:exception:1";

        // 첫 번째 시도: 예외 발생
        assertThatThrownBy(() ->
                redisLockService.executeWithLock(lockKey, Duration.ofSeconds(5), () -> {
                    throw new RuntimeException("작업 중 예외");
                })
        ).isInstanceOf(RuntimeException.class);

        // 락이 해제되었으므로 두 번째 시도 성공
        AtomicBoolean secondSuccess = new AtomicBoolean(false);
        redisLockService.executeWithLock(lockKey, Duration.ofSeconds(5), () -> {
            secondSuccess.set(true);
        });

        assertThat(secondSuccess.get()).as("예외 발생 후 락 자동 해제 확인").isTrue();
    }
}
