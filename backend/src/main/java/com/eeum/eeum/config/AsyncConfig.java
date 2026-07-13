package com.eeum.eeum.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

// @Async 활성화 및 알림/푸시 비동기 처리용 스레드풀 설정
// 알림 리스너(AFTER_COMMIT)는 이 풀에서 실행되어 요청 스레드를 막지 않음

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "applicationTaskExecutor")
    public Executor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-");
        // 큐+풀 포화 시 기본 AbortPolicy면 알림 작업이 예외로 버려진다 → 제출 스레드가 직접 실행(CallerRuns)해
        // 유실 대신 백프레셔로 흡수한다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 배포 재기동 시 큐에 남은 알림 작업을 폐기하지 않고 완료를 기다린다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
