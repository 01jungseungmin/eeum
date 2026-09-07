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
 * 비동기 실행 풀.
 *
 * <p>풀을 둘로 나눈다. 기준은 "포화됐을 때 버려도 되는 작업인가"다.
 *
 * <ul>
 *   <li>{@link #asyncTaskExecutor()} — 알림 <b>생성</b>(DB 저장)을 포함한 일반 비동기 작업.
 *       버리면 알림 레코드가 아예 생기지 않으므로 버릴 수 없다.</li>
 *   <li>{@link #notificationPushTaskExecutor()} — FCM 발송 전용. 이 시점에는 알림이 이미
 *       커밋돼 앱 목록에 보이므로, 포화 시 푸시만 건너뛰는 편이 낫다.</li>
 * </ul>
 *
 * <p>나누는 진짜 이유는 <b>CallerRunsPolicy의 전이 경로</b>다. 하나의 풀을 공유하면 포화 시
 * 요청 스레드가 FCM HTTP 호출과 SSE 소켓 write를 직접 수행한다. 둘 다 수 분까지 블로킹될 수 있고,
 * 그동안 그 스레드는 DB 커넥션을 쥐고 있다(afterCommit 시점에는 아직 반납 전이다).
 * 자원 예산 문서의 R1·R2·R3가 한꺼번에 터지는 경로다.
 *
 * <p>세 번째 풀 {@link #applicationTaskExecutor()}은 Spring MVC async 전용이다.
 * {@code @Async} 작업을 그 이름에 태우지 않는다 — 아래 주석 참고.
 *
 * <p>파라미터를 바꾸면 {@code .claude/skills/references/resource-budget.md}의 표도 함께 고친다.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 일반 비동기 풀 — 한정자 없는 모든 {@code @Async}가 여기로 온다.
     *
     * <p>예전에는 이 빈 이름이 {@code applicationTaskExecutor}였다. 그 이름은 Spring MVC가
     * async 처리용 executor를 <b>이름으로 찾는 자리</b>라, 알림 작업과 MVC async가 한 풀을
     * 나눠 쓰게 된다. 알림이 밀리면 MVC async가 함께 밀리고, 그 반대도 마찬가지다.
     * 이름을 분리하고 MVC용 풀을 따로 뒀다.
     *
     * <p>{@code @Primary}가 필요하다. Executor 빈이 둘 이상이면 한정자 없는 {@code @Async}가
     * 유일 빈 해석에 실패하고, 이름이 {@code taskExecutor}인 빈도 없어
     * <b>SimpleAsyncTaskExecutor로 조용히 내려앉는다</b> — 작업마다 새 스레드를 만드는 무제한 실행이다.
     *
     * <p>큐는 100이다. 500이면 max 16에 도달하기 전에 500개가 먼저 쌓인다 —
     * {@code ThreadPoolExecutor}는 큐가 가득 차야 core를 넘겨 스레드를 늘리기 때문에,
     * 큐가 크면 max 설정이 사실상 죽고 실제 동시 실행은 core 4개에 머문다.
     */
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

    /**
     * Spring MVC async 전용 풀 ({@code Callable}·{@code WebAsyncTask} 반환값 처리).
     *
     * <p>이 이름으로 직접 정의해야 한다. Boot의 자동 설정은
     * {@code @ConditionalOnMissingBean(Executor.class)}라 우리가 Executor 빈을 하나라도
     * 정의하는 순간 꺼진다 — 이름만 바꾸면 되살아나지 않고, MVC async가
     * {@code SimpleAsyncTaskExecutor}(요청마다 새 스레드)로 떨어진다.
     *
     * <p>현재 이 앱의 MVC async는 SSE뿐이고 {@code SseEmitter}는 이 executor를 쓰지 않으므로
     * 실질 사용량은 0이다. 그래도 작게 잡아 둔다 — 나중에 {@code Callable} 반환 엔드포인트가
     * 생겼을 때 무제한 스레드로 시작하지 않게 하는 것이 목적이다.
     *
     * <p>거부 정책은 기본값({@code AbortPolicy})을 그대로 둔다. MVC async에서 CallerRuns는
     * 요청 스레드가 직접 실행한다는 뜻이라 비동기로 뺀 이유 자체가 사라진다.
     */
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

    /**
     * FCM 푸시 전용 풀.
     *
     * <p>포화 시 <b>버린다.</b> 이 작업이 도는 시점에는 알림이 이미 커밋돼 앱 알림 목록에 보이므로
     * 잃는 것은 푸시 한 건이다. 반대로 CallerRuns로 흡수하면 제출 스레드가 FCM HTTP 응답을
     * 기다리게 되고, 그 스레드가 요청 스레드일 수 있다.
     *
     * <p>{@code AbortPolicy} 대신 직접 처리하는 이유는 예외를 제출 지점으로 던지지 않기 위해서다.
     * 푸시를 못 보낸 것이 원 요청을 실패시켜서는 안 된다.
     */
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
