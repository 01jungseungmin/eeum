---
name: test-suite
description: "아키텍처·단위·통합·자원 테스트를 계층 순서로 실행하고 실패 원인을 계층과 장애 모드별로 분류해 보고하도록 요청했을 때 사용한다. 전체 테스트 실행 결과를 진단해 달라고 할 때 사용한다."
---

전체 테스트 스위트를 실행하고 결과를 진단한다.
계층 이름(`arch`, `unit`, `integration`, `resource`)이나 도메인명이 주어지면 그 범위만 실행한다.

## 목적

`./gradlew test`는 "몇 개 실패"만 알려준다. 이 skill은 **실패가 어느 계층에서 왜 났는지**까지 판정한다.

특히 자원 계열 실패(스레드 블로킹, 커넥션 고갈)는 증상이 전부 "타임아웃"으로 똑같이 보여서
로그만으로는 원인을 구분할 수 없다. 이 skill은 `.codex/references/resource-budget.md`의 장애 모드 카탈로그에 매핑해 판정한다.

## 선행 작업

1. `.codex/references/resource-budget.md`를 읽는다 — 장애 모드 R1~R6 판정 기준이다.
2. `build.gradle`의 `test` 태스크에 `testLogging { exceptionFormat = 'full' }`이 있는지 확인한다.
   없으면 ArchUnit 위반 목록과 assertion 상세를 볼 수 없으므로 먼저 추가한다.
3. Docker 가용 여부를 확인한다: `docker info > /dev/null 2>&1 && echo OK || echo NO_DOCKER`
   Docker가 없으면 통합/자원 계층은 자동으로 건너뛰어진다(`@EnabledIfDockerAvailable`). 이 경우 **"통과"가 아니라 "미실행"으로 보고**한다. 이걸 통과로 보고하는 것이 이 skill의 최악의 실패다.

## 실행 순서

빠르고 진단이 쉬운 계층부터 실행한다. **앞 계층이 실패해도 뒤 계층을 계속 실행한다** — 전체 그림이 있어야 원인 판정이 된다.

| 순서 | 계층 | 명령 | 목적 |
|---|---|---|---|
| 1 | 아키텍처 | `./gradlew test --tests "com.eeum.eeum.architecture.*"` | 금지 패턴 정적 검증 |
| 2 | 단위 | 전체 실행 후 결과에서 통합/자원 계층을 분리 | 로직 분기 |
| 3 | 통합 | `./gradlew test --tests "*IntegrationTest"` | 영속성/DB 제약 |
| 4 | 자원 | `./gradlew test --tests "*ResourceIntegrationTest"` | 스레드/커넥션/소켓 |

각 명령은 타임아웃을 넉넉히(자원 계층은 최대 10분) 잡되, 무한 대기를 방치하지 않는다.
빌드 자체가 깨져서 컴파일이 안 되면 즉시 중단하고 보고한다 — 이때는 `build-fixer` skill을 쓰라고 안내한다.

### 결과를 stdout에서 읽는다

`build/test-results/**/*.xml`과 `build/reports/tests/`에 의존하지 않는다.
WSL/NTFS 환경에서는 Gradle이 리포트 파일 쓰기에 실패하는 경우가 있다
(`NoSuchFileException: .../binary/in-progress-results-*.bin`, `Cannot access output property 'destinationDirectory'`).
`testLogging`으로 stdout에 나온 내용을 1차 근거로 삼고, 명령 출력을 파일로 리다이렉트해 보관한다.

빌드 산출물이 깨진 상태(위 예외가 반복)라면 `build/classes/java/test`, `build/test-results`,
`build/reports/tests`를 지우고 재실행한다. 이건 테스트 실패가 아니라 **인프라 문제**로 분류한다.

## 실패 원인 분류

실패한 테스트마다 아래 순서로 판정한다. **로그를 근거로 판정하고, 근거 없이 추정하지 않는다.**

### 1) 인프라 문제인가 (테스트 코드 문제 아님)
- `Could not find a valid Docker environment` → Docker 미기동
- 컨테이너 pull 실패 / 포트 충돌 → 환경 문제
- → **테스트 실패로 집계하지 않는다.** "미실행"으로 분류한다.

### 2) 자원 장애인가 (R1~R6)
로그에서 아래 시그니처를 찾아 매핑한다.

| 시그니처 | 장애 모드 |
|---|---|
| 테스트가 타임아웃, 스택 상단에 `SocketOutputStream.write` / `ResponseBodyEmitter.send` | R1 요청 스레드 블로킹 |
| `SQLTransientConnectionException`, `Connection is not available, request timed out` | R2 커넥션 풀 고갈 |
| `TaskRejectedException`, 또는 `@Async` 작업이 요청 스레드명(`http-nio-`)에서 실행됨 | R3 백프레셔 전이 |
| 반복 후 레지스트리 크기가 0으로 안 돌아옴 | R4 누수 |
| DB는 커밋됐는데 응답이 5xx | R5 콜백 예외 전파 |
| 스케줄러 소요 시간이 예산 초과 | R6 스케줄러 정체 |

판정되면 **관련 프로덕션 코드 위치(파일:라인)를 찾아 함께 보고**한다.

### 3) 아키텍처 규칙 위반인가
ArchUnit 실패는 위반 클래스 목록이 메시지에 그대로 나온다. 규칙명과 위반 목록을 그대로 옮긴다.

### 4) 실제 로직 회귀인가
위 어디에도 해당하지 않으면 기능 회귀다. 어떤 assertion이 무엇을 기대했고 실제 값이 무엇이었는지 적는다.

### 5) 테스트 자체의 결함인가
타이밍 의존(`Thread.sleep`), 테스트 간 데이터 오염, 순서 의존이 의심되면 그렇게 분류한다.
같은 명령을 한 번 더 돌려 **재현되는지 확인**한 뒤에만 "flaky"로 판정한다. 한 번의 실패로 flaky라고 단정하지 않는다.

## 금지 사항

- **테스트나 프로덕션 코드를 수정하지 않는다.** 이 skill은 실행과 진단만 한다. 수정은 `build-fixer` 또는 명시적 요청으로 진행한다.
- 실패한 테스트를 건너뛰거나 `@Disabled`를 제안하며 넘어가지 않는다.
- Docker 부재로 건너뛴 테스트를 통과로 집계하지 않는다.
- 로그를 확인하지 않고 장애 모드를 추정하지 않는다. 근거가 없으면 "판정 불가"로 남긴다.
- 실패 로그를 길게 통째로 붙여넣지 않는다 — 예외 타입, 메시지, 스택 상단 3줄까지만.
- 결과를 낙관적으로 요약하지 않는다. 실패가 있으면 실패라고 먼저 쓴다.

## 출력 형식

```
## 실행 요약
| 계층 | 실행 | 통과 | 실패 | 미실행 |
```

이어서:

1. **실패 목록** — 테스트명 / 분류(인프라·R1~R6·아키텍처·로직회귀·테스트결함) / 근거 로그 한 줄 / 관련 프로덕션 위치
2. **미실행 목록과 사유** (Docker 부재 등)
3. **우선순위 제안** — 자원 장애 > 아키텍처 위반 > 로직 회귀 > 테스트 결함 순. 각각 어느 skill로 이어가면 되는지 명시 (`regression-test`, `arch-rules`, `build-fixer`)
4. 커버되지 않은 영역 — `resource-budget.md`의 R1~R6 중 대응 테스트가 아예 없는 모드
