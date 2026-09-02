package com.eeum.eeum.config;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 큐가 찼을 때 <b>제출 스레드에서 실행하지 않고</b> 자리가 날 때까지 제한 시간 동안 기다린다.
 *
 * <p>{@code CallerRunsPolicy}를 쓸 수 없는 이유가 있다. 이 풀의 작업 상당수는
 * {@code @TransactionalEventListener(AFTER_COMMIT)} + {@code @Async} 조합으로 제출되는데,
 * {@code afterCommit()} 콜백은 {@code cleanupAfterCompletion} <b>이전에</b> 호출되므로
 * 그 시점에는 방금 커밋한 트랜잭션 컨텍스트가 아직 스레드에 바인딩돼 있다.
 *
 * <p>거기서 CallerRuns가 리스너 본문을 실행하면 {@code @Transactional}(REQUIRED)이
 * <b>이미 커밋된 트랜잭션에 참여</b>한다. 참여 트랜잭션은 스스로 커밋하지 않으므로
 * 알림 INSERT가 커밋되지 못하고 조용히 사라진다. 작업을 버리지 않았는데 결과는 유실이다.
 *
 * <p>{@code REQUIRES_NEW}로 분리하는 방법은 쓰지 않는다 — 자원 예산 문서의 금지 패턴 3번이다
 * (afterCommit 경로에서 커넥션을 2개 점유해 풀 고갈로 이어진다).
 *
 * <p><b>남는 위험:</b> 제한 시간 안에 자리가 나지 않으면 작업을 버린다. 유실이 불가능해지는 것이
 * 아니라 <b>드물어지고 로그로 드러나게</b> 된다. 완전한 보장이 필요하면 durable outbox가 답이다.
 * 대기 동안 제출 스레드가 묶이므로 제한 시간은 짧게 잡는다.
 */
@Slf4j
public class WaitForQueueSpacePolicy implements RejectedExecutionHandler {

    private final long timeoutMillis;

    public WaitForQueueSpacePolicy(Duration timeout) {
        this.timeoutMillis = timeout.toMillis();
    }

    @Override
    public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
        if (executor.isShutdown()) {
            log.warn("종료 중이라 비동기 작업을 버린다");
            return;
        }

        try {
            if (executor.getQueue().offer(task, timeoutMillis, TimeUnit.MILLISECONDS)) {
                log.warn("비동기 큐 포화 — 자리가 날 때까지 대기 후 큐잉했다. active={}, queued={}",
                        executor.getActiveCount(), executor.getQueue().size());
                return;
            }
            log.error("비동기 큐 대기 시간({}ms) 초과 — 작업을 버린다. "
                            + "알림이 저장되지 않았을 수 있다. active={}, queued={}",
                    timeoutMillis, executor.getActiveCount(), executor.getQueue().size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("비동기 작업 큐잉 대기 중 인터럽트 — 작업을 버린다");
        }
    }
}
