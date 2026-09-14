package com.eeum.eeum.config;

import com.eeum.eeum.application.operation.service.OperationFailureRecorder;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/** 또 @Scheduled 작업에서 빠져나온 예외를 운영 실패 이력으로 남긴다. */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SchedulingConfig implements SchedulingConfigurer {

    private final OperationFailureRecorder operationFailureRecorder;

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduled-");
        scheduler.setErrorHandler(this::recordSchedulerFailure);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(taskScheduler());
    }

    private void recordSchedulerFailure(Throwable error) {
        log.error("[Scheduler] 처리되지 않은 예외", error);
        operationFailureRecorder.record(
                OperationFailureCategory.SCHEDULER,
                resolveOperationName(error),
                "SCHEDULER",
                null,
                error,
                null
        );
    }

    // 스택트레이스에서 프로젝트 스케줄러 프레임을 찾아 작업명을 만든다.
    // ErrorHandler는 어떤 태스크가 실패했는지 알려주지 않아 이 방법이 유일하다.
    private String resolveOperationName(Throwable error) {
        if (error == null) {
            return "UnknownScheduler";
        }
        for (StackTraceElement frame : error.getStackTrace()) {
            if (frame.getClassName().startsWith("com.eeum.eeum")
                    && frame.getClassName().endsWith("Scheduler")) {
                String simpleName = frame.getClassName()
                        .substring(frame.getClassName().lastIndexOf('.') + 1);
                return simpleName + "." + frame.getMethodName();
            }
        }
        return "UnknownScheduler";
    }
}
