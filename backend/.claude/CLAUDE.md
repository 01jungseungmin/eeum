# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew build

# Run
./gradlew bootRun

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.eeum.eeum.ClassName"

# Clean build
./gradlew clean build
```

## Environment Setup

The app reads from `../.env` (one level above `backend/`). Required env vars:

| Variable | Description |
|---|---|
| `JWT_SECRET` | HS256 secret (required) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | MySQL (defaults to `localhost:3306/eeum`) |
| `REDIS_HOST` / `REDIS_PORT` | Redis (defaults to `localhost:6379`) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Gmail SMTP |
| `NTS_BUSINESS_SERVICE_KEY` | National Tax Service business verification API |
| `KAKAO_REST_API_KEY` | Kakao OAuth |
| `PORTONE_API_SECRET` / `PORTONE_WEBHOOK_SECRET` | PortOne payment gateway |
| `FCM_PROJECT_ID` / `FCM_SERVICE_ACCOUNT_KEY_PATH` | Firebase Cloud Messaging |

Swagger UI is available at `/swagger-ui/index.html` when the server is running.

## Architecture

Java 17, Spring Boot, JPA + QueryDSL, MySQL, Redis.

The codebase uses a four-layer package structure under `com.eeum.eeum`:

```
api/          ← Controllers (REST endpoints)
application/  ← Services, DTOs, mappers, schedulers
domain/       ← JPA entities, repositories, enums, domain events
infrastructure/ ← External adapters (FCM push, SSE)
config/       ← Spring configuration beans
security/     ← JWT filter chain, UserDetails
common/       ← BaseEntity, RedisUtil, RedisLockService, SecurityUtil
exception/    ← ErrorCode enum, exception classes, GlobalExceptionHandler
```

## Coding Rules

### 절대 규칙
- Location, Region, OrderItem을 제외한 모든 엔티티는 `BaseEntity` 상속 필수
- Image 관련 모든 엔티티는 `ImageBase` 상속 필수
- DB 조회만 수행하는 읽기 전용 Service 메서드는 `@Transactional(readOnly = true)` 필수
- 단, WebSocket/SSE close, 외부 API 호출, 파일 I/O 등 장시간 I/O를 함께 수행하는 오케스트레이션 메서드 전체에는 트랜잭션을 걸지 않는다.
  필요한 DB 조회 구간만 별도 read-only 트랜잭션으로 분리하거나, 조회 후 트랜잭션이 종료된 상태에서 I/O를 수행한다.
- 가격 필드는 `BigDecimal` 사용
- API 응답은 반드시 `ApiResponse<T>`로 래핑 (`common/dto/response/ApiResponse`)
- URL은 kebab-case: `/used`, `/store-reviews`
- FCM 직접 호출 금지 — 항상 도메인 이벤트 경유
- 로깅은 SLF4J 사용 — `System.out.println` 금지
- Soft Delete 대상 외 엔티티에 `deletedAt` 추가 금지
- 복잡한 조건 쿼리는 QueryDSL 사용 — JPQL 문자열 직접 작성 금지
- 의존성 주입은 `@RequiredArgsConstructor` 생성자 주입 — 필드 `@Autowired` 금지
- Controller는 엔티티를 직접 반환 금지 — Service에서 DTO로 변환 후 반환

### 엔티티 작성 규칙
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + `@Getter` — Setter 금지
- 생성은 정적 팩토리 메서드 (`ChatRoom.createGroup(...)`), 상태 변경은 의도가 드러나는 도메인 메서드 (`deactivate()`, `updateLastMessageAt()`)
- ID는 `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- 연관관계는 `@ManyToOne(fetch = FetchType.LAZY)` — EAGER 금지
- Polymorphic 참조(`refType` + `refId`)는 FK 없이 사용 (예: `ChatRoom`, `Favorite`)

### 예외 처리 규칙
- 비즈니스 에러는 `BusinessException` 또는 서브클래스(`NotFoundException`, `ForbiddenException`, `ConflictException`, `BadRequestException`)를 `ErrorCode`와 함께 throw:
```java
throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
```
- 새 에러는 `exception/ErrorCode` enum에 추가 — 네이밍은 `{도메인}_{설명}` (예: `CHAT_ROOM_NOT_FOUND`)
- Controller/Service에서 try-catch로 비즈니스 예외 삼키기 금지 — `GlobalExceptionHandler`가 일괄 처리

### 트랜잭션 분리
- 쓰기: `@Transactional` (REQUIRED, 기본값)
- 읽기: `@Transactional(readOnly = true)`
- 감사 로그: `@Transactional(propagation = REQUIRES_NEW)` — 본 작업 실패해도 로그 기록
- 장시간 I/O(WebSocket/SSE/외부 API)를 포함하는 오케스트레이션 메서드는 트랜잭션 밖에서 수행하고, DB 작업만 별도 트랜잭션 경계로 분리한다.

### Soft Delete vs Hard Delete
Soft Delete (deletedAt 필드) 적용 대상:
- `Account` — 탈퇴 후 30일 유예, `AccountCleanupScheduler`가 처리
- `ChatMessage`
- `CommunityComment` — `isDeleted` tombstone으로 댓글·대댓글 스레드 문맥 유지
- `UsedProduct` — 판매완료 글에 후기·채팅·신고 이력이 매달려 물리 삭제 시 참조가 끊김

그 외 엔티티는 Hard Delete (즉시 물리 삭제).
`Category`는 Soft Delete 대상이 아니다 — `deletedAt` 없이 `isActive` 토글로 노출만 끊는다.

### 동시성 제어
재고 차감, 주문 생성, 예약 처리는 `RedisLockService` 사용 필수:
```java
// LockKeys에 정의된 패턴 사용
redisLockService.executeWithLock(LockKeys.ORDER + orderId, () -> { ... });
```

중고거래·찜·회원 탈퇴 연계 쓰기는 기본적으로
`Account → Store/UsedProduct → Favorite/UsedProductImage` 순서로 잠근다.
여러 대상 행을 잠그면 ID 오름차순처럼 하나의 전역 순서를 사용한다. 상세 공개 정책,
카운터, 페이징, 운영 DDL과 필수 경쟁 시나리오는
`.claude/skills/references/used-favorite-review.md`를 따른다.

### 멱등성 처리
- PortOne Webhook: Redis + DB Unique 제약으로 중복 처리 방지
- ChatRoom 생성: 동일 참여자 조합으로 중복 생성 방지

### 결제·정산 검토 게이트
- 결제, 환불, 취소, 수익 원장, 정산, PortOne 변경 및 전체 리뷰는
  `.claude/skills/references/payment-settlement.md`를 반드시 읽고 적용한다.
- 외부 API 요청·응답·Webhook·상태 enum은 리뷰 시점의 공식 문서와 다시 대조한다.
- PortOne Mock 단위 테스트만으로 계약 검증을 완료했다고 판단하지 않는다.
- 계약 변경은 실제 PortOne 호출 대신 공식 fixture와 로컬 HTTP Stub을 사용한 계약 테스트를 추가한다.
- 사용자가 "결제·정산 전체 리뷰"를 요청하면 Git diff로 범위를 축소하지 않는다.

### 페이징 선택 기준
- 무한 스크롤 (모바일 앱): `CursorSlice<T>` (`common/dto/response/CursorSlice`)
  - 새 행이 목록 맨 앞에 꽂히는 정렬(최신순·최근 대화순)은 OFFSET 금지 — 페이지 사이 삽입
    한 건에 목록이 통째로 밀려 경계 항목이 중복·누락된다
  - 커서는 정렬 키를 전부 담는다. PK tie-break를 빼면 같은 값 구간에서 같은 결함이 재현된다
  - 다음 커서(`nextCursorValue`/`nextCursorId`)는 서버가 만들어 응답에 싣고, 클라이언트는
    그대로 되돌려보낸다. 한쪽만 보내거나 형식이 깨지면 400
  - `Slice<T>`를 쓰지 않는다 — 페이지 번호가 없는데 `number=0`, `first=true`가 실려
    응답이 실제 위치를 잘못 설명한다
- 관리자 페이지 (번호 페이징): `Page<T>`

### 신규 도메인 단계별 개발
- 신규 도메인이나 큰 기능 확장은 `.claude/skills/references/domain-development-workflow.md`의
  Gate 1~7을 순서대로 적용한다.
- 정책 결정 → Domain/Persistence → Application/Transaction → API/DTO → 교차 도메인·운영 →
  테스트 → 최종 전체 리뷰 순서를 지키고, 이전 Gate의 위반을 다음 단계로 넘기지 않는다.
- 완성 구현에서 공개 범위, 권한, 상태 전이, 삭제, 외부 계약처럼 결과를 바꾸는 정책이 미정이면
  TODO나 임의 값으로 진행하지 않고 사용자 결정을 받는다.

### 테스트 작성 규칙
- JUnit5 + Mockito + AssertJ 조합 (Spring Boot test starter에 포함)
- Given-When-Then 구조로 작성
- 테스트 메서드명 한글 허용: `주문_생성_시_재고가_차감된다()`
- 단위 테스트는 외부 의존성(Repository, Redis, FCM, PortOne) 전부 Mocking — `@ExtendWith(MockitoExtension.class)`
- 검증은 AssertJ `assertThat` 사용 — JUnit `assertEquals` 금지
- 테스트 위치는 프로덕션 코드와 동일한 패키지 구조: `src/test/java/com/eeum/eeum/application/order/...`
- 단위 테스트는 외부 의존성을 전부 Mocking하므로 **스레드·DB 커넥션·소켓 같은 런타임 자원 문제를 구조적으로 검증하지 못한다.**
  서버 무응답, 요청 타임아웃, 커넥션 풀 고갈, 연결 누수를 다룰 때는
  `.claude/skills/references/resource-budget.md`를 읽고 `/resource-test`를 사용한다.

### 스케줄러 목록
새 스케줄러 추가 전 반드시 기존 목록 확인 (위치: `application/{domain}/scheduler/`):
- `AccountCleanupScheduler` — 매일 03:00, 탈퇴 후 30일 경과 계정 **개인정보 파기(익명화)**. 계정 행은 남긴다 — 주문·결제·신고·후기 등 다수 테이블이 참조하고 일부는 보존 의무가 있어 물리 삭제할 수 없다. `Account.anonymize()`가 email·nickname·name·phone·password·FCM 토큰을 지우고 `anonymizedAt`을 남기며, 참조가 끊겨도 되는 자식(찜·활동지역·사업자정보·정산계좌)만 함께 삭제한다
- `OrderExpirationScheduler` — 1분 주기, 결제 대기(PENDING) 15분 경과 주문 만료 처리
- `NotificationCleanupScheduler` — 매일 03:00 6개월 이전 알림 삭제 / 5분 주기 Redis unread 카운트 ↔ DB 정합성 보정
- `AiScheduledMessageScheduler` — 1분 주기, scheduledAt 경과한 AI 예약 메시지 발송 (최대 50건/회, 재시도 3회 초과 시 FAILED)
- `AiPlanExpirationScheduler` — 매일 03:30, 만료일 지난 AI 플랜 구독 비활성화 (이후 FREE 처리)
- `AiPlanPaymentExpirationScheduler` — 1분 주기, 결제 대기(PENDING) 15분 경과 AI 플랜 결제 FAILED 처리
- `OperationFailureLogCleanupScheduler` — 매일 04:00, 보존 기간(3개월) 지난 운영 실패 이력 물리 삭제
- `NotificationOutboxScheduler` — 1초 주기, `notification_outbox`의 대기 행을 처리해 알림 생성 (한 번에 100건, 재시도 5회 초과 시 FAILED) / 매일 04:20 완료분(24시간 경과) 정리. 알림 생성은 비동기 이벤트가 아니라 이 경로다 — 원 트랜잭션에서 outbox에 기록하고 여기서 꺼내 쓴다
- `WebSocketSessionReconciliationScheduler` — 30초 주기, 붙어 있는 WebSocket 세션의 계정 상태·토큰 세대를 DB와 대조해 회수된 연결 종료. **분산 잠금을 걸지 않는다**(`@InstanceLocalSchedule`) — 세션은 JVM 안에만 있어 한 대만 돌면 나머지 인스턴스 세션이 방치된다

### Redis 키 패턴
새 키 추가 시 기존 패턴과 충돌 금지:
- `refresh:{accountId}` — refresh token
- `blacklist:access:{token}` — 로그아웃된 access token
- `reauth:{accountId}` / `password-reset:{accountId}` — 일회용 토큰
- `unread:account:{accountId}` — 알림 unread 카운트 캐시 (전체)
- `unread:category:{accountId}` — 알림 unread 카테고리별 카운트 캐시 (hash, 변경 시 무효화)
- `rate-limit:email-verification:{email}` — 이메일 인증 코드 발송 쿨다운 (60초)
- `rate-limit:password-reset:{email}` — 비밀번호 재설정 메일 발송 쿨다운 (5분)
- `rate-limit:login-fail:{email}` — 로그인 실패 카운터 (5분 내 5회 초과 시 차단)
- `rate-limit:operation-failure-alert:{category}` — 운영 실패 관리자 알림 스로틀 (분류별 10분 쿨다운)
- 분산 락 키는 `common/lock/LockKeys`에 상수로 정의 후 사용
- Rate Limit 키는 `common/lock/RateLimitKeys`에 상수로 정의 후 사용 (`common/service/RateLimitService`로 체크)

### 도메인 이벤트 사용 시점
외부 연동(FCM 푸시, SSE)은 직접 호출하지 말고 도메인 이벤트로 처리:
```java
eventPublisher.publishEvent(new OrderPaidEvent(order));
// Listener에서 @TransactionalEventListener(AFTER_COMMIT) + @Async 처리
```

### Domain modules

Each domain lives in its own sub-package across `api/`, `application/`, and `domain/`:

- **account** — `Account` entity (roles: `ROLE_USER`, `ROLE_OWNER`, `ROLE_ADMIN`), `OwnerInfo` for pending owner applications, `AccountRegion` for GPS-verified activity regions
- **auth** — Login (local + Kakao OAuth), email verification, token reissue, business registration verification via NTS API
- **store** — `Store` entity, `StoreBusinessHour`, `StoreImage`, `StoreNotice`, `StoreReview`/`StoreReviewReply`
- **product** — `Product`, `ProductCategory`, `ProductOption`/`ProductOptionItem`, `EventProduct` (flash-sale events)
- **order** — `Cart`/`CartItem`, `Order`/`OrderItem`, `Payment`; PortOne 연동
    - Cart는 동일 Store 상품만 담기 가능 — 다른 Store 상품 추가 시 Cart 전체 초기화
    - 주문 생성 시 재고 차감은 반드시 분산 락 적용
    - PortOne Webhook은 멱등성 처리 필수 (동일 paymentId 2회 호출 → 1회만 처리)
- **reservation** — `VisitReservation`, `VisitReservationTimeSlot`, `StoreVisitReservationSetting`
    - 동일 시간 슬롯 동시 예약 시 슬롯의 `maxVisitorCount` / `maxTeamCount` 초과 차단 — 분산 락 적용
- **chat** — `ChatRoom` (1:1 and group), `ChatMessage`, `ChatParticipant`; unread count tracked in Redis
    - 동일 참여자 조합으로 ChatRoom 중복 생성 금지 — 기존 방이 있으면 그 방을 반환 (멱등성)
- **notification** — `Notification`, `NotificationSettings`; push via FCM, real-time via SSE
- **favorite** — Polymorphic `Favorite` keyed by `FavoriteRefType`
- **region** — `Region` (administrative region lookup), `Location` for GPS coordinate storage
- **used** — `UsedProduct` (C2C 중고거래 게시글). 거래 상태(`SELLING`/`RESERVED`/`SOLD`)·관리자 숨김(`hidden`)·Soft Delete(`deletedAt`)를 독립된 세 축으로 관리한다. 카테고리는 `CategoryType.USED`를 재사용하고, 거래 희망 지역은 작성 시점 `Region`을 복사해 고정한다.

### Key design patterns

**JWT + Redis token management** (`security/jwt/JwtProvider`, `application/auth/service/TokenService`): Four token types — `ACCESS`, `REFRESH`, `REAUTH`, `PASSWORD_RESET`. Refresh tokens are stored in Redis under `refresh:{accountId}`; access tokens are blacklisted on logout under `blacklist:access:{token}`. Reauth and password-reset tokens are one-time-use, stored under `reauth:{accountId}` and `password-reset:{accountId}`.

**Role-based access** (`config/SecurityConfig`): Routes are split into public GET/POST (no auth), `/admin/**` (ROLE_ADMIN), `/owner/**` (ROLE_OWNER), and authenticated-only. Owner approval flow starts as `ROLE_USER` and is promoted to `ROLE_OWNER` after admin approval.

**Redis distributed lock** (`common/service/RedisLockService`): Uses `SET NX` with a Lua release script to prevent double-processing of payments, orders, and reservations. Lock key patterns are defined in `common/lock/LockKeys`.

**Domain events** (`ApplicationEventPublisher`): Used to decouple side effects. Examples: `OrderPaidEvent`, `ChatMessageSentEvent`, `OwnerApplicationSubmittedEvent`. Notification push listeners use `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` so FCM calls happen after the DB transaction commits without blocking the response.

**QueryDSL custom repositories**: Complex queries use the `*RepositoryCustom` interface + `*RepositoryImpl` pattern with `JPAQueryFactory`. See `domain/*/repository/*RepositoryImpl.java`. Q클래스는 빌드 시 자동 생성 — 엔티티 변경 후 Q클래스 오류가 나면 `./gradlew clean build`.

**BaseEntity**: All entities extend `BaseEntity` which provides JPA-audited `createdAt` / `modifiedAt` fields.

**Error handling**: All business errors throw subclasses of `BusinessException` (or `NotFoundException`, `ForbiddenException`, `ConflictException`, `BadRequestException`) with an `ErrorCode` enum entry. `GlobalExceptionHandler` maps these to structured JSON responses.

**FCM push adapter**: `infrastructure/push/PushAdapter` has two implementations — `FcmPushAdapter` (production) and `NoOpPushAdapter` (no FCM config). Real-time notifications also use SSE via `infrastructure/sse/SseEmitterManager`.

## Git Conventions

- PR 베이스 브랜치는 `develop` (`main` 직접 머지 금지)
- 브랜치 네이밍: `feature/{기능명}` (예: `feature/chat`, `feature/notification`)
- 커밋 메시지: `feat:`, `fix:`, `refactor:` prefix + 한글 설명

## 마무리

- 모든 기능이 종료된 후 바뀐 부분에 대한 설명과 이유를 작성해서 정리
- 테스트면 테스트로 기능이면 기능으로 묶어서 출력
