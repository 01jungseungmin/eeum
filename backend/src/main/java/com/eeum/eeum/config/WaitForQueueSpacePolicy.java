package com.eeum.eeum.config;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 큐가 차면 제출 스레드에서 실행하지 않고 자리가 날 때까지 제한 시간만 기다린다.
 *
 * CallerRuns를 쓸 수 없다. afterCommit 콜백은 트랜잭션 컨텍스트가 아직 스레드에 바인딩된
 * 상태라, 거기서 리스너 본문을 실행하면 REQUIRED가 이미 커밋된 트랜잭션에 참여해 INSERT가
 * 조용히 사라진다. REQUIRES_NEW 분리는 커넥션 2개 점유라 금지(resource-budget.md 금지 패턴 3).
 * 제한 시간을 넘기면 버린다 — 유실이 사라지는 게 아니라 드물어지고 로그에 드러난다.
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
