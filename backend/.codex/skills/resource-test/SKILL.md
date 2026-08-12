---
name: resource-test
description: "스레드·DB 커넥션·소켓·비동기 풀 같은 런타임 자원의 고갈, 블로킹, 누수를 재현하는 통합 테스트를 요청했을 때 사용한다. 서버 무응답, 요청 타임아웃, 커넥션 풀 고갈, SSE/WebSocket 연결 누수의 원인을 테스트로 검증하고 싶을 때 사용한다."
---

사용자가 지정한 경로 또는 도메인에 대한 자원 고갈 테스트를 작성한다.
지정이 없으면 `.codex/references/resource-budget.md`의 장애 모드 카탈로그 순서대로 미작성 시나리오를 찾아 만든다.

## 목적

`test-gen`(단위)은 자원을 Mocking해서 없애고, `integration-test`(통합)는 **영속성/DB 제약**을 본다.
이 skill은 세 번째 축인 **런타임 자원**을 담당한다: 스레드, DB 커넥션, 소켓, 비동기 풀.

핵심 아이디어는 **자원 예산을 의도적으로 최소치로 줄이는 것**이다.
운영에서는 커넥션 10개, 스레드 200개라서 문제가 부하가 걸릴 때만 드러나지만,
테스트에서 커넥션을 2개로 줄이면 **동시 요청 3건만으로 즉시 재현**된다.

이 방식의 가장 큰 가치는 **특정 버그를 미리 알지 못해도 시나리오를 세울 수 있다**는 점이다.
"커넥션 2개로 동시 10건" 같은 조건은 일반 조건이라, 아직 발견되지 않은 같은 계열의 버그도 함께 잡는다.

## 선행 작업

1. `.codex/references/resource-budget.md`를 읽는다 — 자원 예산과 장애 모드 카탈로그(R1~R6)가 있다.
2. `src/test/java/com/eeum/eeum/application/product/service/EventProductConcurrencyIntegrationTest.java`를 읽는다 — Testcontainers 컨벤션의 단일 기준이다.
3. 대상 경로의 코드를 읽고, **어느 스레드에서 실행되고 어떤 자원을 언제까지 쥐는지** 먼저 정리한다. 이 정리 없이 테스트를 쓰지 않는다.

## 시나리오 템플릿

장애 모드별로 아래 형태를 사용한다. 대상에 맞게 구체화하되, 골격은 유지한다.

### R2 — 커넥션 풀 고갈/데드락

```
given: @DynamicPropertySource 로 spring.datasource.hikari.maximum-pool-size = 2,
       connection-timeout = 3000 으로 축소
when : 대상 API/Service를 CountDownLatch로 동시 10건 호출
then : 전부 성공하고, 어떤 호출도 SQLTransientConnectionException 을 던지지 않는다
```

한 요청이 커넥션을 2개 이상 점유하면(대표적으로 `afterCommit` 안의 `REQUIRES_NEW`) 여기서 반드시 걸린다.

### R1 — 요청 스레드 블로킹

```
given: 대상이 SSE/WebSocket 등 장기 연결을 쓴다면, 응답을 읽지 않는 raw Socket 으로 연결을 맺고 유지
       (java.net.Socket 으로 HTTP 요청만 쓰고 InputStream 을 읽지 않는다)
when : 그 계정에 대해 알림/이벤트를 유발하는 다른 API를 호출
then : 해당 API가 Awaitility 기준 3초 안에 응답한다
```

블로킹 write가 요청 스레드에 있으면 이 테스트가 타임아웃으로 실패한다.
`@LocalServerPort`를 받아 실제 포트로 소켓을 연결한다 — MockMvc로는 재현되지 않는다.

### R3 — 비동기 풀 포화 / 백프레셔 전이

```
given: @TestConfiguration 으로 applicationTaskExecutor 를 core=1, queue=1, CallerRunsPolicy 로 교체
when : @Async 리스너를 유발하는 API를 동시 20건 호출
then : 각 API 응답이 3초 안에 완료된다 (CallerRuns 로 요청 스레드가 잠식되지 않는다)
```

### R4 — 연결/객체 누수

```
given: 정상 경로로 N회 연결/해제를 반복
when : 중간에 예외 경로를 강제로 한 번 발생시킨다 (의존 Bean을 spy 로 감싸 throw)
then : 내부 레지스트리(emitters 맵 등)의 크기가 0으로 돌아온다
```

레지스트리 크기는 `ReflectionTestUtils.getField(...)`로 확인한다. 프로덕션 코드에 테스트 전용 getter를 추가하지 않는다.

### R5 — 콜백 예외의 응답 오염

```
given: 커밋 후 콜백에서 호출되는 협력 객체가 RuntimeException 을 던지도록 스텁
when : 대상 API 호출
then : API 는 2xx 로 응답하고, DB 변경은 커밋되어 있다 (재조회로 확인)
```

DB는 커밋됐는데 API가 500을 반환하면 클라이언트가 재시도해 중복 요청을 만든다. 이 테스트가 그걸 막는다.

### R6 — 단일 스레드 스케줄러 정체

```
given: 대상 스케줄러가 순회하는 데이터를 N건(수백) 만들어 둔다
when : 스케줄러 메서드를 직접 호출하고 소요 시간을 측정
then : 예산 시간(스케줄 주기의 1/2) 안에 끝난다
```

## 작성 컨벤션

`EventProductConcurrencyIntegrationTest`의 어노테이션/컨테이너 설정을 그대로 따르되, 아래를 추가한다.

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` — R1은 실제 소켓이 필요하므로 필수
- `@EnabledIfDockerAvailable` 필수 — Docker 없는 환경에서 빌드가 깨지면 안 된다
- `@ActiveProfiles("test")`
- 자원 축소는 `@DynamicPropertySource`로 한다 — `src/test/resources/application-test.yml`을 수정해서 **다른 테스트의 예산까지 바꾸지 않는다**
- 동시성은 `CountDownLatch` + `ExecutorService`로 만들고, 대기는 `Thread.sleep`이 아니라 **Awaitility**를 쓴다 (`org.awaitility:awaitility`가 이미 의존성에 있다)
- 모든 대기에 명시적 타임아웃을 건다 — 타임아웃 없는 테스트는 CI를 영구히 매달리게 한다
- 테스트 클래스에 `@Transactional`을 걸지 않는다 — 자동 롤백은 커밋 후 콜백을 아예 실행시키지 않아 검증 대상이 사라진다
- 파일명은 `{도메인}ResourceIntegrationTest.java`, 위치는 프로덕션과 동일 패키지
- 메서드명은 한글로 조건과 기대를 모두 담는다: `커넥션_풀이_2개일_때_알림_생성_10건이_동시에_들어와도_전부_성공한다()`

## 실패한 테스트를 다루는 방법

이 skill로 만든 테스트는 **처음에 실패하는 것이 정상**이다. 그게 발견이다.

- 실패했다고 프로덕션 코드를 고치지 않는다 — 이 skill은 테스트만 만든다.
- 실패했다고 임계값을 느슨하게 조정하지 않는다 (3초를 30초로 늘리는 행위 금지).
- 실패한 테스트에 `@Disabled`를 붙이지 않는다. 실패 상태로 남기고 **보고서에 명시**한다.
- 단, 대상 코드의 결함이 아니라 **테스트 설계 오류**로 실패한 경우는 테스트를 고친다. 둘을 구분해 보고한다.

## 금지 사항

- H2 등 인메모리 DB로 대체하지 않는다 — 실제 커넥션 풀 동작 검증이 목적이다.
- 프로덕션 코드에 테스트 전용 getter/setter/필드를 추가하지 않는다.
- 외부 API(PortOne, FCM, 카카오, NTS)를 실제로 호출하는 시나리오를 만들지 않는다.
- Secret, API Key를 생성하거나 하드코딩하지 않는다.
- `integration-test`가 이미 다루는 영속성/FK 시나리오를 중복 생성하지 않는다.

## 출력 형식

1. 대상 경로에 대해 정리한 **스레드/자원 점유표** (단계 → 실행 스레드 → 점유 자원 → 반납 시점)
2. 생성한 테스트 파일 경로와 시나리오 목록 (각각 장애 모드 ID R1~R6 표기)
3. `./gradlew test --tests "*ResourceIntegrationTest"` 실행 결과
4. **실패한 테스트와 그 원인 분석** — 프로덕션 결함인지 테스트 설계 오류인지 구분
5. 만들지 않은 시나리오와 사유 (외부 API 의존 등)
