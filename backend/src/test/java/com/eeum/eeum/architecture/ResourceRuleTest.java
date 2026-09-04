package com.eeum.eeum.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

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

    /**
     * 소켓 write를 한 군데로 가둔다.
     *
     * <p>예전 규칙은 "{@code sendUnreadCount}를 부르는 메서드는 {@code @Async}여야 한다"였다.
     * 호출부마다 비동기를 강제하는 방식이라 호출부가 늘 때마다 빠뜨리기 쉬웠고, 실제로
     * 위반 2건을 안고 비활성 상태였다. 지금은 {@code SseEmitterManager}가 전송을 자기
     * 전용 풀로 넘기므로 호출부는 블로킹되지 않는다. 대신 지켜야 할 것은
     * <b>그 경로를 우회해 직접 write하지 않는 것</b>이고, 그건 이렇게 검사하는 편이 정확하다.
     */
    @ArchTest
    static final ArchRule SseEmitter_전송은_SseEmitterManager_안에서만_한다 =
            noClasses()
                    .that().resideOutsideOfPackage("com.eeum.eeum.infrastructure.sse..")
                    .should().callMethodWhere(ArchConditions.callTo(ResponseBodyEmitter.class, "send"))
                    .because("R1 — SseEmitter.send()는 블로킹 소켓 write다. 요청 스레드나 "
                            + "Redis 리스너 스레드에서 직접 호출하면 죽은 클라이언트의 TCP 재전송 "
                            + "타임아웃까지 그 스레드가 묶인다. 전송은 SseEmitterManager의 전용 풀로 넘긴다");

    // TODO(arch): 아직 비활성. 규칙은 옳으므로 삭제하지 말 것.
    //
    // NotificationService.markAsReadByRefForAccounts 위반은 해소됐다 — unread 재계산이
    // @Async 스레드로 넘어가 트랜잭션 안에서 REQUIRES_NEW를 열지 않는다.
    //
    // 남은 위반 1건 — AiPlanPaymentCommandExecutor. 실패 이력을 본 트랜잭션과 독립적으로
    // 커밋하려는 의도된 사용이라 규칙을 그대로 켜면 잡힌다. 이 규칙이 실제로 막고 싶은 것은
    // "커넥션을 2개 쥔 채 오래 머무는 것"이지 "실패 기록을 따로 커밋하는 것"이 아니므로,
    // 켜기 전에 조건을 그쪽으로 좁혀야 한다.
    //
    // 그리고 이 조건은 직접 호출만 본다. 이번에 실제로 문제였던 경로는
    // afterCommit 람다 안에서의 호출이라 합성 메서드(lambda$…)가 호출자로 잡혀
    // 규칙을 켰더라도 걸리지 않았다. 조건을 넓힐 때 함께 다룬다.
    @ArchIgnore
    @ArchTest
    static final ArchRule REQUIRES_NEW를_트랜잭션_안에서_직접_호출하지_않는다 =
            methods()
                    .should(ArchConditions.notBeCalledFromTransactionalMethod())
                    .because("R2 — 바깥 트랜잭션은 suspend 돼도 커넥션을 반납하지 않아 한 요청이 커넥션 2개를 점유한다");
}
