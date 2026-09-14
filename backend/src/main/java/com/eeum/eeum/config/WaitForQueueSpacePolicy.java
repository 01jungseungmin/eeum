package com.eeum.eeum.config;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** CallerRuns로 커밋된 트랜잭션에 참여하지 않도록 큐 여유를 제한 시간만큼 기다린다. */
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
