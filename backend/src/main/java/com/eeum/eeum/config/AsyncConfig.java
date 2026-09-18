package com.eeum.eeum.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * 비동기 실행 풀. 포화 시 버려도 되는 작업인지를 기준으로 나눈다.
 *
 * 알림 생성(DB 저장)은 버리면 레코드가 아예 안 생기고, FCM 발송은 이미 커밋된 알림의
 * 푸시라 건너뛰어도 된다. 한 풀을 공유하면 포화 시 제출 스레드가 FCM HTTP·SSE write에
 * 묶이는데, afterCommit 시점에는 DB 커넥션을 쥔 채라 풀 고갈로 번진다.
 * MVC async 풀은 이름이 겹치면 알림과 서로 밀리므로 따로 둔다(resource-budget.md 갱신 필요).
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 한정자 없는 @Async의 기본 풀. @Primary가 없으면 무제한 실행기로 폴백할 수 있다. */
    @Primary
    @Bean(name = "asyncTaskExecutor")
    public Executor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        // CallerRuns를 쓰지 않는다. 이 풀의 작업은 AFTER_COMMIT 콜백에서 제출되는 경우가 많은데,
        // 그 스레드에서 실행하면 @Transactional이 이미 커밋된 트랜잭션에 참여해
        // 알림 INSERT가 커밋되지 못한다(WaitForQueueSpacePolicy 주석 참고).
        executor.setRejectedExecutionHandler(new WaitForQueueSpacePolicy(Duration.ofSeconds(2)));
        // 배포 재기동 시 큐에 남은 알림 작업을 폐기하지 않고 완료를 기다린다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "chatBroadcastTaskExecutor")
    public Executor chatBroadcastTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 메시지의 체감 순서는 채팅 기능의 계약이다. Presigned URL 생성만 비동기로 빼되
        // 단일 FIFO 워커로 Redis 발행 순서를 보존한다.
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("chat-broadcast-");
        // 큐 포화가 커밋 완료 콜백을 다시 요청 스레드에서 막으면 비동기 분리 의미가 사라진다.
        // 메시지는 REST 조회로 재동기화할 수 있으므로 이 경우 실시간 1건만 건너뛴다.
        executor.setRejectedExecutionHandler((task, poolExecutor) ->
                log.warn("채팅 실시간 중계 큐 포화 — REST 재동기화 대기. queued={}",
                        poolExecutor.getQueue().size()));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** Spring MVC async 전용 풀. 무제한 스레드 폴백을 막기 위해 이름을 직접 등록한다. */
    @Bean(name = "applicationTaskExecutor")
    public Executor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mvc-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /** FCM 푸시 전용 풀. 포화 시 푸시만 버리고 이미 저장된 알림은 보존한다. */
    @Bean(name = "notificationPushTaskExecutor")
    public Executor notificationPushTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 외부 HTTP 대기가 대부분이라 CPU 수와 무관하게 잡는다.
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("push-");
        executor.setRejectedExecutionHandler((task, poolExecutor) ->
                log.warn("푸시 큐 포화 — FCM 발송을 건너뛴다. 알림 자체는 저장돼 앱에서 보인다. "
                        + "activeThreads={}, queued={}", poolExecutor.getActiveCount(),
                        poolExecutor.getQueue().size()));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
