package com.eeum.eeum.config;

import com.eeum.eeum.application.notification.listener.NotificationPushEventListener;
import com.eeum.eeum.domain.notification.event.NotificationPushEvent;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비동기 풀 배선 검증.
 *
 * <p>여기서 막는 것은 <b>조용히 잘못되는</b> 두 가지다.
 *
 * <ol>
 *   <li>Executor 빈이 둘이 되면서 한정자 없는 {@code @Async}가 유일 빈 해석에 실패하는 것.
 *       실패해도 예외가 나지 않고 SimpleAsyncTaskExecutor로 내려앉아 작업마다 새 스레드를 만든다.
 *       테스트가 없으면 운영에서 스레드 폭증으로만 드러난다.</li>
 *   <li>푸시 풀이 CallerRunsPolicy로 되돌아가는 것. 그러면 포화 시 제출 스레드가
 *       FCM HTTP 응답을 기다리고, 그 스레드가 요청 스레드일 수 있다.</li>
 * </ol>
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class AsyncExecutorWiringIntegrationTest extends IntegrationTestSupport {

    private final TaskExecutor defaultAsyncExecutor;

    // 이름으로 꺼낸다. @RequiredArgsConstructor는 @Qualifier를 생성자 파라미터로 복사하지 않아
    // 필드에 붙여도 무시되고 @Primary 빈이 주입된다 — 검증 대상이 뒤바뀐다.
    private final ApplicationContext applicationContext;

    private ThreadPoolTaskExecutor pushExecutor() {
        return applicationContext.getBean("notificationPushTaskExecutor", ThreadPoolTaskExecutor.class);
    }

    private ThreadPoolTaskExecutor mvcAsyncExecutor() {
        return applicationContext.getBean("applicationTaskExecutor", ThreadPoolTaskExecutor.class);
    }

    @Test
    void 한정자_없는_비동기는_일반_풀로_해석된다() {
        // Given & When: Executor 빈이 둘이므로 @Primary가 없으면 유일 빈 해석이 깨진다.
        //               깨져도 예외가 아니라 SimpleAsyncTaskExecutor 폴백이라 조용히 지나간다.

        // Then
        assertThat(defaultAsyncExecutor)
                .isInstanceOf(ThreadPoolTaskExecutor.class);
        assertThat(((ThreadPoolTaskExecutor) defaultAsyncExecutor).getThreadNamePrefix())
                .isEqualTo("async-");
        // MVC용 빈이 기본으로 잡히면 알림 작업이 MVC async 풀로 흘러간다
        assertThat(defaultAsyncExecutor).isNotSameAs(mvcAsyncExecutor());
    }

    @Test
    void 푸시_풀은_일반_풀과_분리돼_있다() {
        // Given & When & Then: 같은 풀을 쓰면 알림 생성이 밀릴 때 FCM 발송도 함께 밀리고,
        //                      포화 시 백프레셔가 요청 스레드까지 전이된다.
        assertThat(pushExecutor()).isNotSameAs(defaultAsyncExecutor);
        assertThat(pushExecutor().getThreadNamePrefix()).isEqualTo("push-");
    }

    @Test
    void 푸시_풀은_포화_시_요청_스레드로_넘기지_않고_버린다() {
        // Given: 이 시점에는 알림이 이미 커밋돼 앱 목록에 보인다 — 잃는 것은 푸시 한 건뿐이다.
        //        반대로 CallerRuns면 제출 스레드가 FCM 응답을 기다린다.
        ThreadPoolExecutor pool = pushExecutor().getThreadPoolExecutor();

        // When & Then
        assertThat(pool.getRejectedExecutionHandler())
                .isNotInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class)
                .isNotInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
    }

    @Test
    void 일반_풀은_포화_시_작업을_버리지_않는다() {
        // Given: 이 풀의 작업은 알림 레코드를 만든다. 버리면 알림이 영영 생기지 않는다.
        ThreadPoolExecutor pool =
                ((ThreadPoolTaskExecutor) defaultAsyncExecutor).getThreadPoolExecutor();

        // When & Then
        assertThat(pool.getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
    }

    @Test
    void 일반_풀은_큐가_차기_전에_스레드를_늘릴_수_있다() throws Exception {
        // Given: ThreadPoolExecutor는 큐가 가득 차야 core를 넘겨 스레드를 늘린다.
        //        큐가 max보다 훨씬 크면 max 설정이 죽고 실제 동시 실행은 core에 머문다.
        ThreadPoolExecutor pool =
                ((ThreadPoolTaskExecutor) defaultAsyncExecutor).getThreadPoolExecutor();

        // When & Then: 큐 용량이 max 대비 과도하지 않아야 max가 의미를 가진다
        int queueCapacity = pool.getQueue().remainingCapacity() + pool.getQueue().size();
        assertThat(pool.getMaximumPoolSize()).isGreaterThan(pool.getCorePoolSize());
        assertThat(queueCapacity).isLessThanOrEqualTo(100);
    }

    @Test
    void MVC_async_풀은_비동기_작업_풀과_분리돼_있다() {
        // Given: applicationTaskExecutor는 Spring MVC가 async executor를 "이름으로 찾는" 자리다.
        //        @Async 작업을 그 이름에 태우면 알림이 밀릴 때 MVC async도 함께 밀린다.

        // When & Then
        assertThat(mvcAsyncExecutor()).isNotSameAs(defaultAsyncExecutor);
        assertThat(mvcAsyncExecutor().getThreadNamePrefix()).isEqualTo("mvc-async-");
    }

    @Test
    void MVC_async_풀이_비어_있지_않다() {
        // Given: Boot의 자동 설정은 @ConditionalOnMissingBean(Executor.class)라
        //        우리가 Executor 빈을 하나라도 정의하면 꺼진다. 이름만 바꾸고 두면
        //        MVC async가 SimpleAsyncTaskExecutor(요청마다 새 스레드)로 떨어진다.

        // When & Then
        assertThat(applicationContext.containsBean("applicationTaskExecutor")).isTrue();
        assertThat(mvcAsyncExecutor().getThreadPoolExecutor().getMaximumPoolSize())
                .isPositive();
    }

    @Test
    void 푸시_리스너는_푸시_풀을_지정해_쓴다() throws Exception {
        // Given & When
        Method handler = NotificationPushEventListener.class
                .getMethod("onPushEvent", NotificationPushEvent.class);

        // Then: 한정자가 빠지면 일반 풀로 돌아가 외부 HTTP가 다시 섞인다
        assertThat(handler.getAnnotation(Async.class).value())
                .isEqualTo("notificationPushTaskExecutor");
    }
}
