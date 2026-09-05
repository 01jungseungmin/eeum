package com.eeum.eeum.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 큐 포화 시 제출 스레드가 작업을 직접 실행하지 않는지 검증한다.
 *
 * <p>이것이 {@code CallerRunsPolicy}와 갈리는 지점이고, 이 프로젝트에서 실제로 문제가 되는
 * 지점이다. AFTER_COMMIT 콜백 스레드가 리스너 본문을 실행하면 {@code @Transactional}이
 * 이미 커밋된 트랜잭션에 참여해 DB 쓰기가 조용히 사라진다.
 *
 * <p>스레드풀 동작만 보므로 Spring 컨텍스트가 필요 없다.
 */
class WaitForQueueSpacePolicyTest {

    private ThreadPoolExecutor pool(Duration timeout) {
        return new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new WaitForQueueSpacePolicy(timeout));
    }

    @Test
    void 큐가_차도_제출_스레드에서_실행하지_않는다() throws Exception {
        // Given: 워커 1개 + 큐 1칸. 세 번째 제출부터 거부된다.
        //        제출을 별도 스레드에서 한다 — 거부된 제출은 자리가 날 때까지 대기하므로
        //        같은 스레드에서 워커를 풀어줄 수 없다.
        ThreadPoolExecutor pool = pool(Duration.ofSeconds(5));
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(3);
        Set<String> executingThreads = ConcurrentHashMap.newKeySet();

        Thread submitter = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                pool.execute(() -> {
                    executingThreads.add(Thread.currentThread().getName());
                    awaitQuietly(release);
                    done.countDown();
                });
            }
        }, "submitter-thread");

        try {
            // When: 제출자가 큐 대기에 들어간 것을 확인한 뒤에 워커를 풀어준다.
            //       이 확인이 있어야 "거부가 실제로 일어났다"가 보장된다.
            submitter.start();
            assertThat(awaitBlockedInQueueWait(submitter))
                    .as("제출자가 큐 대기에 들어가지 않았다 — 거부가 재현되지 않음")
                    .isTrue();
            release.countDown();

            submitter.join(10_000);
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

            // Then: CallerRuns였다면 제출 스레드 이름이 섞인다
            assertThat(executingThreads).doesNotContain("submitter-thread");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    // 거부된 제출은 BlockingQueue.offer(timeout)에서 TIMED_WAITING이 된다.
    private boolean awaitBlockedInQueueWait(Thread submitter) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (submitter.getState() == Thread.State.TIMED_WAITING) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    @Test
    void 자리가_나면_거부된_작업도_결국_실행된다() throws Exception {
        // Given: 유실 대신 대기로 흡수하는 것이 이 정책의 목적이다
        ThreadPoolExecutor pool = pool(Duration.ofSeconds(5));
        CountDownLatch done = new CountDownLatch(3);

        try {
            // When
            for (int i = 0; i < 3; i++) {
                pool.execute(done::countDown);
            }

            // Then
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 제한_시간_안에_자리가_안_나면_버린다() throws Exception {
        // Given: 무한 대기하면 제출 스레드(요청 스레드일 수 있다)가 영영 묶인다.
        //        유실을 없애는 것이 아니라 드물게 만들고 로그로 드러내는 것이 목적이다.
        ThreadPoolExecutor pool = pool(Duration.ofMillis(200));
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);

        try {
            pool.execute(() -> {
                started.countDown();
                awaitQuietly(release);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            pool.execute(() -> { });  // 큐 1칸 차지

            // When: 세 번째는 거부되고, 대기해도 자리가 안 나 버려진다
            long before = System.nanoTime();
            pool.execute(() -> { });
            long waitedMillis = (System.nanoTime() - before) / 1_000_000;

            // Then: 예외를 던지지 않고, 무한정 기다리지도 않는다
            assertThat(waitedMillis).isBetween(150L, 3_000L);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
