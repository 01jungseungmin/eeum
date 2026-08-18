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

/**
 * {@code @Scheduled} 작업에서 빠져나온 예외를 운영 실패 이력으로 남긴다.
 *
 * <p>이전에는 스케줄러가 던진 예외를 Spring 기본 핸들러가 로그로만 흘려보내
 * "어젯밤 주문 만료 배치가 돌긴 했나"에 답할 수 없었다.
 *
 * <p>ErrorHandler 방식을 쓴 이유: 스케줄러마다 try-catch를 넣으면 현재 9개 파일을 모두 고쳐야 하고
 * 앞으로 추가되는 스케줄러에서 빠뜨리기 쉽다. 여기서 한 번 등록하면 전부 자동으로 걸린다.
 * (AOP는 {@code spring-boot-starter-aop} 의존성이 추가로 필요해 선택하지 않았다.)
 *
 * <p>풀 크기는 1로 둔다 — Spring 기본값과 같아 기존 실행 순서·동시성이 그대로 유지된다.
 * 늘리면 지금까지 직렬 실행에 의존하던 스케줄러들이 병렬로 겹칠 수 있다.
 */
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
