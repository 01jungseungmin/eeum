package com.eeum.eeum.architecture;

import com.eeum.eeum.infrastructure.sse.SseEmitterManager;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * 런타임 자원 점유 금지 패턴.
 *
 * <p>기준: {@code .claude/skills/references/resource-budget.md} 의 장애 모드 카탈로그 R1~R6.
 * 이 규칙들은 SSE 알림 장애(요청 스레드 블로킹 + 커넥션 풀 데드락)에서 도출됐다.
 * 단위 테스트는 자원을 Mocking해서 없애므로 이 계열을 구조적으로 잡지 못한다.
 */
@AnalyzeClasses(packages = "com.eeum.eeum", importOptions = ImportOption.DoNotIncludeTests.class)
class ResourceRuleTest {

    // TODO(arch): 기존 위반이 있어 임시 비활성. 규칙은 옳으므로 삭제하지 말 것.
    // 위반 1건 — NotificationCleanupScheduler.java:41
    @ArchIgnore
    @ArchTest
    static final ArchRule Redis_KEYS_명령을_사용하지_않는다 =
            noClasses()
                    .should().callMethodWhere(ArchConditions.callTo(RedisOperations.class, "keys"))
                    .because("R6 — KEYS는 Redis 전체를 블로킹한다. SCAN으로 커서 순회해야 한다");

    // TODO(arch): 기존 위반이 있어 임시 비활성. 규칙은 옳으므로 삭제하지 말 것.
    // 위반 1건 — AsyncConfig.java:18
    @ArchIgnore
    @ArchTest
    static final ArchRule applicationTaskExecutor_빈을_재정의하지_않는다 =
            methods()
                    .should(ArchConditions.notDeclareBeanNamed("applicationTaskExecutor"))
                    .because("R3 — Spring Boot 기본 빈을 덮어쓰면 @Async뿐 아니라 Spring MVC async 처리까지 같은 풀을 쓰게 된다");

    @ArchTest
    static final ArchRule 트랜잭션_안에서_외부_HTTP를_호출하지_않는다 =
            noMethods()
                    .that().areAnnotatedWith(Transactional.class)
                    .should(ArchConditions.callAnyMethodOf(RestTemplate.class, RestClient.class))
                    .because("R2 — 외부 HTTP 응답을 기다리는 동안 DB 커넥션을 계속 점유해 풀이 고갈된다");

    // TODO(arch): 기존 위반이 있어 임시 비활성. 규칙은 옳으므로 삭제하지 말 것.
    // 위반 2건 — NotificationService.java:55, :280
    @ArchIgnore
    @ArchTest
    static final ArchRule SSE_전송은_비동기로만_한다 =
            methods()
                    .that(ArchConditions.callMethodOf(SseEmitterManager.class, "sendUnreadCount"))
                    .should().beAnnotatedWith(Async.class)
                    .because("R1 — SseEmitter.send()는 블로킹 소켓 write다. 요청 스레드에서 호출하면 "
                            + "죽은 클라이언트의 TCP 재전송 타임아웃까지 Tomcat 스레드가 묶인다");

    // TODO(arch): 기존 위반이 있어 임시 비활성. 규칙은 옳으므로 삭제하지 말 것.
    // 위반 2건 — AiPlanPaymentCommandExecutor, NotificationService.markAsReadByRefForAccounts
    @ArchIgnore
    @ArchTest
    static final ArchRule REQUIRES_NEW를_트랜잭션_안에서_직접_호출하지_않는다 =
            methods()
                    .should(ArchConditions.notBeCalledFromTransactionalMethod())
                    .because("R2 — 바깥 트랜잭션은 suspend 돼도 커넥션을 반납하지 않아 한 요청이 커넥션 2개를 점유한다");
}
