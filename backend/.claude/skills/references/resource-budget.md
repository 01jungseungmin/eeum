# 자원 예산 (Resource Budget)

이음(Eeum) 백엔드가 런타임에 점유하는 **유한 자원**과 그 예산을 정의한다.

`arch-rules`, `resource-test`, `regression-test`, `test-suite` 커맨드가 공유하는 단일 기준이다.
자원 설정이 바뀌면 이 파일만 수정한다.

## 왜 이 파일이 필요한가

이 프로젝트의 단위 테스트는 규칙상 Repository/Redis/FCM/PortOne을 전부 Mocking한다.
그래서 **스레드, DB 커넥션, 소켓처럼 "모킹되는 순간 사라지는 자원"** 은 단위 테스트로 구조적으로 검증할 수 없다.

실제로 SSE 알림 장애(요청 스레드 블로킹 + 커넥션 풀 데드락)는 단위 테스트 100여 개를 모두 통과한 상태에서 발생했다.
자원 축은 별도 검증 체계가 필요하다.

## 자원 목록과 현재 예산

아래 "기본값"은 `src/main/resources/application.yml`에 해당 설정이 **없어서** 프레임워크 기본값이 적용된다는 뜻이다.
설정을 추가하면 이 표를 함께 갱신한다.

| 자원 | 현재 예산 | 출처 | 고갈 시 증상 |
|---|---|---|---|
| Tomcat 워커 스레드 | 200 (기본값) | 설정 없음 | 요청이 큐에 쌓이다 타임아웃 — 서버가 꺼진 것처럼 보임 |
| Tomcat 최대 커넥션 | 8192 (기본값) | 설정 없음 | 신규 연결 거부, FD 고갈 |
| HikariCP 커넥션 풀 | **10 (기본값)** | 설정 없음 | `Connection is not available, request timed out after 30000ms` |
| `applicationTaskExecutor` | core 4 / max 16 / queue 500 / CallerRunsPolicy | `config/AsyncConfig.java` | 큐 포화 시 백프레셔가 **요청 스레드로 전이** |
| `@Scheduled` 스레드 | **1 (기본값)** | 설정 없음 | 느린 스케줄러 하나가 나머지 전부를 지연시킴 |
| SSE emitter | 계정당 1개 / 타임아웃 30분 / **하트비트 없음** | `infrastructure/sse/SseEmitterManager.java` | 죽은 연결이 최대 30분 생존, 그 사이 write가 블로킹 |
| Redis 커넥션 | Lettuce 공유 커넥션 | 설정 없음 | `KEYS` 등 블로킹 명령이 전체 지연 |

### 예산 계산 시 반드시 고려할 것

- **`applicationTaskExecutor`는 Spring Boot 기본 빈 이름을 덮어쓴다.** Boot 3.2+/4에서는 이 빈이 Spring MVC async 처리에도 사용되므로 영향 범위가 `@Async`에 국한되지 않는다.
- **`queueCapacity=500` 때문에 스레드는 사실상 4개다.** `ThreadPoolExecutor`는 큐가 가득 차기 전까지 core를 넘겨 스레드를 늘리지 않는다. `maxPoolSize=16`은 큐에 500개가 쌓인 뒤에야 의미가 생긴다.
- **`REQUIRES_NEW`는 커넥션을 2개 점유한다.** 바깥 트랜잭션은 suspend 되어도 커넥션을 반납하지 않는다. 풀 크기가 10이면 이런 요청은 **동시 5개**가 상한이고, 10개가 동시에 들어오면 전원이 두 번째 커넥션을 기다리는 데드락이 된다.
- **`afterCommit`은 별도 스레드가 아니다.** `TransactionSynchronization.afterCommit()`은 커밋을 수행한 그 스레드(대개 Tomcat 요청 스레드)에서 실행되며, 이 시점에 바깥 트랜잭션의 커넥션은 **아직 반납되지 않았다**(반납은 `afterCompletion` 이후).
- **`SseEmitter.send()`는 블로킹 소켓 write다.** 클라이언트가 TCP FIN 없이 사라지면(모바일 네트워크 단절, 앱 백그라운드, 프록시 idle cut) 송신 버퍼가 찬 뒤 TCP 재전송 타임아웃(리눅스 기본 수 분~15분)까지 스레드가 묶인다. `ResponseBodyEmitter.send()`는 `synchronized`이므로 같은 emitter를 만지는 다른 스레드도 함께 묶인다.

## 장애 모드 카탈로그

테스트/규칙을 만들 때 아래 6가지 모드에 매핑한다.

| ID | 장애 모드 | 대표 원인 패턴 |
|---|---|---|
| R1 | 요청 스레드 블로킹 | `afterCommit`/`@Transactional` 안에서 소켓 write, 외부 HTTP 호출, 락 대기 |
| R2 | DB 커넥션 풀 고갈/데드락 | 한 요청이 커넥션 2개 이상 점유 (`REQUIRES_NEW` 중첩), 트랜잭션 안 외부 I/O |
| R3 | 비동기 풀 포화 → 백프레셔 전이 | `CallerRunsPolicy` + 느린 작업 + 작은 core 크기 |
| R4 | 연결/emitter 누수 | 정리 콜백 미등록, 예외 경로에서 맵에 고아 객체 잔존, 하트비트 부재 |
| R5 | 예외 전파로 인한 요청 실패 | 콜백에서 좁은 타입만 catch, `afterCommit` 예외가 커밋 밖으로 전파 |
| R6 | 단일 스레드 스케줄러 정체 | 블로킹 Redis 명령(`KEYS`), 전 계정 순회 + 행 락 |

## 정적으로 금지할 패턴 (arch-rules 대상)

R1·R2·R3·R6을 **코드 작성 시점에** 차단한다.

1. `afterCommit`·`TransactionSynchronization` 콜백 안에서 `SseEmitter.send`, `RestTemplate`, `RestClient` 호출 금지
2. `@Transactional` 메서드 안에서 외부 HTTP 클라이언트(`RestTemplate`, `RestClient`) 직접 호출 금지
3. `@Transactional` 또는 `afterCommit` 실행 경로에서 `Propagation.REQUIRES_NEW` 메서드 호출 금지 (커넥션 2배 점유)
4. `SseEmitterManager`의 전송 메서드는 요청 스레드에서 직접 호출 금지 — 반드시 `@Async` 경유
5. `RedisTemplate.keys(...)` 호출 금지 — `scan` 사용
6. `@Bean` 이름 `applicationTaskExecutor` 금지 — Spring Boot 기본 빈을 덮어써 MVC async에까지 영향
7. `@Scheduled` 메서드 안에서 전체 계정 순회 + 비관적 락 조합 금지

## 동적으로 검증할 항목 (resource-test 대상)

R1·R2·R4·R5는 정적 규칙만으로 부족하므로 **자원을 일부러 굶긴** 통합 테스트로 검증한다.
핵심은 "버그를 미리 알지 못해도 세울 수 있는 일반 조건"으로 만드는 것이다.

- 커넥션 풀을 2로 줄이고 동시 요청 N건 → 전원 성공 여부
- 비동기 풀 core를 1로 줄이고 알림 다건 발생 → API 응답 시간
- 응답을 읽지 않는 raw TCP 소켓으로 SSE 연결 유지 → 다른 API의 응답 시간
- 예외 경로를 강제한 뒤 emitter/커넥션이 회수되는지
- 콜백에서 런타임 예외 발생 시 API 응답 코드가 오염되는지

## 이 파일을 갱신해야 하는 시점

- `AsyncConfig`의 풀 파라미터를 바꿨을 때
- `application.yml`에 `spring.datasource.hikari`, `server.tomcat`, `spring.task` 설정을 추가했을 때
- 새 스레드풀·커넥션풀·장기 연결(SSE/WebSocket) 자원을 도입했을 때
- 새로운 장애 모드를 실제로 겪었을 때 (카탈로그에 ID를 추가한다)
