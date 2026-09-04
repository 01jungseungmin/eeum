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
| HikariCP 커넥션 풀 | **20** / connection-timeout 5s / max-lifetime 29분 / leak-detection 20s | `application.yml` (`spring.datasource.hikari`) | `Connection is not available, request timed out after 5000ms` |
| `asyncTaskExecutor` (`@Async` 기본) | core 4 / max 16 / queue 100 / `WaitForQueueSpacePolicy(2s)` / `@Primary` | `config/AsyncConfig.java` | 큐 포화 시 제출 스레드가 최대 2초 대기, 그래도 자리가 없으면 작업 폐기(ERROR 로그) |
| `applicationTaskExecutor` (MVC async 전용) | core 2 / max 8 / queue 100 / AbortPolicy | `config/AsyncConfig.java` | `Callable` 반환 엔드포인트가 거부됨 (현재 사용처 없음) |
| `notificationPushTaskExecutor` | core 4 / max 8 / queue 200 / **포화 시 폐기(로그)** | `config/AsyncConfig.java` | FCM 발송 누락 (알림 레코드는 이미 커밋돼 앱에는 보임) |
| `@Scheduled` 스레드 | **1 (기본값)** | 설정 없음 | 느린 스케줄러 하나가 나머지 전부를 지연시킴 |
| SSE emitter | 계정당 1개 / 타임아웃 30분 / 하트비트 30초 | `infrastructure/sse/SseEmitterManager.java` | 하트비트 실패로 죽은 연결을 즉시 회수 |
| SSE write 풀 (`sse-write`) | core 2 / max 8 / queue 500 / **포화 시 폐기(로그)** | `infrastructure/sse/SseEmitterManager.java` | 배지 전송 누락 (다음 이벤트나 재조회로 복구) |
| Redis 일반 중계 풀 (`relay-`) | core 2 / max 8 / queue 200 / 포화 시 폐기(로그) | `infrastructure/realtime/RealtimeRelaySubscriber.java` | STOMP·unread 실시간 이벤트 1건 유실 |
| Redis 세션 종료 풀 (`relay-session-termination-`) | core 1 / max 1 / queue 1 / 포화 시 Redis 리스너가 직접 처리 | `infrastructure/realtime/RealtimeRelaySubscriber.java` | 종료 신호를 일반 실시간 이벤트 때문에 폐기하지 않음 |
| Redis 커넥션 | Lettuce 공유 커넥션 | 설정 없음 | `KEYS` 등 블로킹 명령이 전체 지연 |

### 예산 계산 시 반드시 고려할 것

- **`applicationTaskExecutor`는 Spring MVC가 async executor를 이름으로 찾는 자리다.** 여기에 `@Async` 작업을 태우면 알림이 밀릴 때 MVC async도 함께 밀린다. `@Async`용 풀은 `asyncTaskExecutor`로 분리하고 `@Primary`로 기본 해석을 잡는다.
- **Boot의 task 자동 설정은 Executor 빈 하나만 있어도 꺼진다.** `@ConditionalOnMissingBean(Executor.class)`이므로(Boot 4.0.6 확인), `applicationTaskExecutor`라는 이름을 비워 두면 Boot가 채워주는 것이 아니라 MVC async가 `SimpleAsyncTaskExecutor`(요청마다 새 스레드)로 떨어진다. 그 이름의 빈은 **직접 정의해야** 한다.
- **큐 용량이 크면 `maxPoolSize`가 죽는다.** `ThreadPoolExecutor`는 큐가 가득 차기 전까지 core를 넘겨 스레드를 늘리지 않는다. queue를 500에서 100으로 줄여 max 16이 실제로 도달 가능하게 했다.
- **Executor 빈이 둘 이상이면 한정자 없는 `@Async`가 조용히 깨진다.** 유일 빈 해석에 실패하고 이름이 `taskExecutor`인 빈도 없으면 `SimpleAsyncTaskExecutor`(작업마다 새 스레드)로 폴백한다. 예외가 나지 않으므로 `applicationTaskExecutor`에 `@Primary`가 필요하다. `config/AsyncExecutorWiringIntegrationTest`가 이 배선을 고정한다.
- **버려도 되는 작업과 아닌 작업을 같은 풀에 두지 않는다.** 알림 생성(DB 저장)은 버리면 알림이 영영 생기지 않아 CallerRuns로 흡수해야 하고, FCM 발송은 이미 커밋된 알림의 전달일 뿐이라 버리는 편이 낫다. 한 풀에 두면 후자를 위해 전자를 버리거나, 전자를 위해 요청 스레드가 외부 HTTP를 기다린다.
- **`REQUIRES_NEW`는 커넥션을 2개 점유한다.** 바깥 트랜잭션은 suspend 되어도 커넥션을 반납하지 않는다. 풀 크기가 10이면 이런 요청은 **동시 5개**가 상한이고, 10개가 동시에 들어오면 전원이 두 번째 커넥션을 기다리는 데드락이 된다.
- **`CallerRunsPolicy`를 `@Async` 풀에 쓰면 안 된다.** 이 풀의 작업 상당수는 `AFTER_COMMIT` 콜백에서 제출된다. 그 스레드에서 실행되면 `@Transactional`(REQUIRED)이 **이미 커밋된 트랜잭션에 참여**해 DB 쓰기가 커밋되지 못하고 조용히 사라진다. 작업을 버리지 않았는데 결과는 유실이다. `REQUIRES_NEW`로 피하는 방법은 금지 패턴 3번에 걸린다(커넥션 2배 점유). 그래서 `WaitForQueueSpacePolicy`로 **제출 스레드 실행 자체를 없앴다** — 유실이 불가능해지는 것은 아니고 드물어지고 로그로 드러난다. 완전한 보장이 필요하면 durable outbox가 답이다.
- **`afterCommit`은 별도 스레드가 아니다.** `TransactionSynchronization.afterCommit()`은 커밋을 수행한 그 스레드(대개 Tomcat 요청 스레드)에서 실행되며, 이 시점에 바깥 트랜잭션의 커넥션은 **아직 반납되지 않았다**(반납은 `afterCompletion` 이후).
- **소켓 write는 `SseEmitterManager`의 전용 풀에서만 한다.** 호출부(요청 스레드·Redis 리스너 스레드)는 큐에 넣고 즉시 반환한다. 포화 시 전송을 버리는 이유는 아래 항목과 같다 — 호출 스레드를 붙잡는 대가가 훨씬 크다.
- **`SseEmitter.send()`는 블로킹 소켓 write다.** 클라이언트가 TCP FIN 없이 사라지면(모바일 네트워크 단절, 앱 백그라운드, 프록시 idle cut) 송신 버퍼가 찬 뒤 TCP 재전송 타임아웃(리눅스 기본 수 분~15분)까지 스레드가 묶인다. `ResponseBodyEmitter.send()`는 `synchronized`이므로 같은 emitter를 만지는 다른 스레드도 함께 묶인다.

## 장애 모드 카탈로그

테스트/규칙을 만들 때 아래 6가지 모드에 매핑한다.

| ID | 장애 모드 | 대표 원인 패턴 |
|---|---|---|
| R1 | 요청 스레드 블로킹 | `afterCommit`/`@Transactional` 안에서 소켓 write, 외부 HTTP 호출, 락 대기 |
| R2 | DB 커넥션 풀 고갈/데드락 | 한 요청이 커넥션 2개 이상 점유 (`REQUIRES_NEW` 중첩), 트랜잭션 안 외부 I/O |
| R3 | 비동기 풀 포화 → 백프레셔 전이 | `CallerRunsPolicy` + 느린 작업 + 작은 core 크기. **외부 I/O 작업이 같은 풀에 있으면 요청 스레드가 소켓·HTTP를 기다린다** |
| R4 | 연결/emitter 누수 | 정리 콜백 미등록, 예외 경로에서 맵에 고아 객체 잔존, 하트비트 부재 |
| R5 | 예외 전파로 인한 요청 실패 | 콜백에서 좁은 타입만 catch, `afterCommit` 예외가 커밋 밖으로 전파 |
| R7 | `afterCommit` 스레드의 DB 쓰기 미커밋 | `CallerRunsPolicy` + `AFTER_COMMIT` + `@Transactional`(REQUIRED) — 이미 커밋된 트랜잭션에 참여 |
| R6 | 단일 스레드 스케줄러 정체 | 블로킹 Redis 명령(`KEYS`), 전 계정 순회 + 행 락 |

## 정적으로 금지할 패턴 (arch-rules 대상)

R1·R2·R3·R6을 **코드 작성 시점에** 차단한다.

1. `afterCommit`·`TransactionSynchronization` 콜백 안에서 `SseEmitter.send`, `RestTemplate`, `RestClient` 호출 금지
2. `@Transactional` 메서드 안에서 외부 HTTP 클라이언트(`RestTemplate`, `RestClient`) 직접 호출 금지
3. `@Transactional` 또는 `afterCommit` 실행 경로에서 `Propagation.REQUIRES_NEW` 메서드 호출 금지 (커넥션 2배 점유)
4. `SseEmitter.send`(= `ResponseBodyEmitter.send`)를 `infrastructure/sse` 밖에서 호출 금지 — 전송은 `SseEmitterManager`의 전용 풀을 거친다. (예전 규칙은 "`sendUnreadCount` 호출부가 `@Async`여야 한다"였다. 호출부마다 비동기를 강제하는 방식이라 호출부가 늘 때마다 빠뜨렸고, 실제로 위반을 안고 비활성 상태였다.)
5. `RedisTemplate.keys(...)` 호출 금지 — `scan` 사용
6. `applicationTaskExecutor` 빈에 `@Async` 작업을 태우지 않는다 — 그 이름은 Spring MVC async 전용 자리다. `@Async` 풀은 별도 이름 + `@Primary`로 둔다. (이름의 빈 자체는 MVC용으로 필요하다 — 위 "고려할 것" 참고)
7. `@Scheduled` 메서드 안에서 전체 계정 순회 + 비관적 락 조합 금지 — 보정 작업은 배치 집계로 먼저 비교하고 <b>어긋난 대상에만</b> 락을 건다

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
