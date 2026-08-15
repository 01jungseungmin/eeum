---
description: CLAUDE.md 절대 규칙과 자원 점유 금지 패턴을 ArchUnit 테스트로 고정
allowed-tools: Read, Write, Edit, Grep, Glob, Bash
---

$ARGUMENTS 범위의 아키텍처 규칙을 ArchUnit 테스트로 작성하거나 갱신하세요.
인자가 없으면 전체 규칙 세트를 점검하고 누락된 규칙만 추가합니다.

## 목적

`CLAUDE.md`의 "절대 규칙"과 `references/resource-budget.md`의 금지 패턴은 지금 **사람이 리뷰로만** 지키고 있다.
리뷰는 놓치고, 놓친 규칙은 시간이 지나 장애가 된다.

ArchUnit은 이 규칙들을 **컴파일된 바이트코드 기준으로 CI에서 몇 초 만에** 검증한다.
단위/통합 테스트와 달리 시나리오를 상상할 필요가 없고, 규칙을 한 번 박아두면 앞으로 작성될 코드에도 영구히 적용된다.

## 선행 작업

1. `.claude/skills/references/resource-budget.md`를 읽는다 — 금지 패턴의 단일 기준이다.
2. `.claude/CLAUDE.md`의 "절대 규칙", "엔티티 작성 규칙", "트랜잭션 분리" 절을 읽는다.
3. `build.gradle`에 ArchUnit 의존성이 있는지 확인한다. 없으면 `dependencies` 블록의 `testImplementation` 그룹에 추가한다:
   ```
   testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
   ```
   Spring Boot BOM이 관리하지 않는 의존성이라 버전 명시가 필요하다.
   추가했으면 `./gradlew compileTestJava`로 해석되는지 먼저 확인한다.
4. `build.gradle`의 `test` 태스크에 실패 로그 설정이 있는지 확인한다. 없으면 추가한다:
   ```gradle
   testLogging { events 'failed'; exceptionFormat = 'full'; showStackTraces = false }
   ```
   **이 설정이 없으면 이 커맨드는 무의미하다.** ArchUnit은 위반 목록을 예외 메시지 본문에만 담기 때문에,
   `exceptionFormat = 'full'` 없이는 "N개 실패"만 보이고 어느 클래스가 위반인지 알 수 없다.
   즉 아래 "출력 형식"의 3번(핵심 산출물)을 만들 수 없다.

## 작성 위치와 구조

`src/test/java/com/eeum/eeum/architecture/` 아래에 관심사별로 분리한다.

| 파일 | 담당 |
|---|---|
| `LayerRuleTest.java` | 4-layer 의존 방향 |
| `EntityRuleTest.java` | 엔티티 작성 규칙 |
| `TransactionRuleTest.java` | 트랜잭션 경계 |
| `ResourceRuleTest.java` | 자원 점유 금지 패턴 (resource-budget 기준) |
| `ApiRuleTest.java` | Controller/API 계약 |

클래스 공통으로 `@AnalyzeClasses(packages = "com.eeum.eeum", importOptions = ImportOption.DoNotIncludeTests.class)`를 사용하고,
규칙은 `@ArchTest static ArchRule` 필드로 선언한다.

## 규칙 목록

아래는 **최소 세트**다. 이미 존재하는 규칙은 중복 생성하지 않고, 누락된 것만 추가한다.

### ResourceRuleTest — 오늘의 장애에서 도출된 규칙 (최우선)

- `TransactionSynchronization` 구현체 또는 `afterCommit` 이름의 메서드 안에서 `SseEmitter`, `RestTemplate`, `RestClient`를 호출하지 않는다 (R1)
- `@Transactional`이 붙은 메서드는 `RestTemplate`/`RestClient`를 직접 호출하지 않는다 (R2)
- `@Transactional(propagation = REQUIRES_NEW)` 메서드는 `@Transactional` 메서드에서 호출되지 않는다 (R2)
- `SseEmitterManager`의 전송 메서드를 호출하는 클래스는 `@Async` 경유여야 한다 (R1)
- `RedisTemplate.keys(..)` / `StringRedisTemplate.keys(..)`를 호출하지 않는다 (R6)
- `@Bean` 메서드 이름 또는 `@Bean(name=...)`에 `applicationTaskExecutor`를 쓰지 않는다 (R3)
- `@Scheduled` 메서드는 `...WithLock` 이름의 Repository 메서드를 반복문 안에서 호출하지 않는다 (R6)

REQUIRES_NEW 호출 관계처럼 ArchUnit의 기본 조건으로 표현하기 어려운 규칙은
`ArchCondition`을 직접 구현하거나, 정확한 표현이 불가능하면 **규칙을 만들지 말고 제외 사유를 보고**한다.
부정확한 규칙은 false positive를 낳고 결국 주석 처리되어 사라진다.

#### 이미 표현 불가로 판정된 규칙 (재검토 불필요)

아래 두 규칙은 실제 시도 후 ArchUnit으로 정확히 표현할 수 없다고 판정됐다. 다시 시도하지 말고 이 판정을 인용한다.

- **`afterCommit` 콜백 안의 SSE/HTTP 호출 금지** — 이 프로젝트는 `runAfterCommit(() -> ...)` 람다를 거치므로 바이트코드상 호출자가 합성 메서드(`lambda$…`)이고 어노테이션이 없다. `afterCommit` 이름 기준 규칙으로는 아무것도 잡히지 않는다. 대신 "SSE 전송은 `@Async`여야 한다"가 같은 결함을 정확히 잡는다.
- **`@Scheduled` 메서드의 반복문 안 비관적 락 호출 금지** — ArchUnit에 제어 흐름 분석이 없어 "반복문 안"을 표현할 수 없다. 이 항목은 `resource-test`의 R6 시나리오에서 동적으로 검증한다.


### EntityRuleTest

- `domain..entity` 패키지의 `@Entity` 클래스는 `BaseEntity`를 상속한다 — 예외: `Location`, `Region`, `OrderItem`
- Image 관련 엔티티는 `ImageBase`를 상속한다
- 엔티티에 `setXxx` 메서드가 없다 — **`@Setter` 어노테이션으로 검사하지 말 것.** Lombok의 `@Setter`는 `RetentionPolicy.SOURCE`라 바이트코드에 남지 않아 ArchUnit이 볼 수 없다. 생성된 결과물인 `setXxx` 메서드를 검사한다 (손으로 쓴 setter까지 잡혀 더 강한 규칙이다)
- `@ManyToOne` / `@OneToOne` 필드는 `fetch = FetchType.LAZY`다
- `deletedAt` 필드는 허용 목록(`Account`, `ChatMessage`, `Category`) 밖의 엔티티에 없다
- 가격 의미의 필드(`price`, `amount`, `totalPrice` 등)는 `BigDecimal`이다

### TransactionRuleTest

- 이름이 `get`/`find`/`search`/`count`로 시작하는 Service public 메서드는 `@Transactional(readOnly = true)`다
- Service는 `@Transactional`을 붙인 메서드에서 FCM/PushAdapter를 직접 호출하지 않는다 (도메인 이벤트 경유)

### LayerRuleTest

- `domain..`은 `application..`, `api..`, `infrastructure..`에 의존하지 않는다
- `api..`는 `domain..repository`에 직접 의존하지 않는다
- `application..`은 `api..`에 의존하지 않는다

### ApiRuleTest

- `@RestController` 클래스의 public 메서드는 `domain..entity` 타입을 반환하지 않는다
- Controller는 필드 `@Autowired`를 쓰지 않는다
- 프로덕션 코드는 `System.out` / `System.err`에 접근하지 않는다

## 작성 컨벤션

- 규칙마다 `.because("...")`에 **왜 금지인지와 위반 시 장애 모드 ID**를 한국어로 적는다. 예: `.because("R1 — afterCommit은 요청 스레드에서 실행되므로 소켓 write가 Tomcat 스레드를 블로킹한다")`
- 기존 위반이 있어 즉시 통과시킬 수 없는 규칙은 삭제하거나 약화하지 말고, `@ArchIgnore`를 붙이고 **위반 목록과 함께 보고**한다. 규칙 자체는 코드에 남긴다.
- 허용 예외(`Location`, `Region`, `OrderItem` 등)는 규칙 안에 상수 배열로 명시하고, 왜 예외인지 주석을 단다.
- 하나의 `@ArchTest`가 여러 규칙을 검사하지 않는다 — 실패 시 원인을 특정할 수 없다.
- **컴파일 전용 의존성을 클래스 리터럴(`X.class`)로 참조하지 않는다.** `@ArchTest` 필드는 클래스 초기화 시점에 평가되므로 해당 타입이 `testRuntimeClasspath`에 없으면 `NoClassDefFoundError`가 나고, **그 한 건이 ArchUnit 엔진의 discovery 전체를 실패시켜 다른 규칙까지 하나도 실행되지 않는다.** 이 프로젝트에서는 `lombok`이 `testCompileOnly`라 대표적인 함정이다. 런타임에 없는 어노테이션은 문자열 이름으로 검사하거나, 아예 바이트코드에 남는 다른 신호로 규칙을 바꾼다.
- 규칙을 추가할 때마다 한 번씩 실행해 본다. 여러 규칙을 몰아 쓴 뒤 한 번에 돌리면 위 discovery 실패 시 원인 규칙을 특정하기 어렵다.

## 실행 및 확인

작성 후 반드시 실행한다:

```bash
./gradlew test --tests "com.eeum.eeum.architecture.*"
```

Docker가 필요 없는 테스트이므로 어떤 환경에서도 돌아야 한다. 돌지 않으면 원인을 해결하고 보고한다.

## 금지 사항

- 규칙을 통과시키기 위해 프로덕션 코드를 수정하지 않는다 — 이 커맨드는 규칙만 만든다. 위반은 보고 대상이다.
- 통과시키려고 규칙의 범위를 좁히지 않는다 (`.that()` 조건에 특정 클래스명을 나열해 회피하는 행위 금지).
- ArchUnit으로 정확히 표현할 수 없는 규칙을 억지로 근사해서 만들지 않는다 — 제외하고 사유를 보고한다.
- `CLAUDE.md`나 `resource-budget.md`에 없는 규칙을 임의로 추가하지 않는다. 필요하다고 판단되면 제안만 한다.

## 출력 형식

1. 추가/갱신한 규칙 파일 경로와 규칙 개수
2. `./gradlew test --tests "com.eeum.eeum.architecture.*"` 실행 결과 (통과/실패 수)
3. **기존 코드 위반 목록** — 규칙별로 위반 클래스와 위치. 이게 이 커맨드의 핵심 산출물이다.
4. ArchUnit으로 표현 불가해 제외한 규칙과 사유
5. `build.gradle`을 수정했다면 그 내용
