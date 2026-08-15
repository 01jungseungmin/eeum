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
- 읽기 전용 메서드는 `@Transactional(readOnly = true)` 필수
- 가격 필드는 `BigDecimal` 사용
- API 응답은 반드시 `ApiResponse<T>`로 래핑 (`common/dto/response/ApiResponse`)
- URL은 kebab-case: `/used-products`, `/store-reviews`
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

### Soft Delete vs Hard Delete
Soft Delete (deletedAt 필드) 적용 대상:
- `Account` — 탈퇴 후 30일 유예, `AccountCleanupScheduler`가 처리
- `ChatMessage`
- `Category`

그 외 엔티티는 Hard Delete (즉시 물리 삭제)

### 동시성 제어
재고 차감, 주문 생성, 예약 처리는 `RedisLockService` 사용 필수:
```java
// LockKeys에 정의된 패턴 사용
redisLockService.executeWithLock(LockKeys.ORDER + orderId, () -> { ... });
```

### 멱등성 처리
- PortOne Webhook: Redis + DB Unique 제약으로 중복 처리 방지
- ChatRoom 생성: 동일 참여자 조합으로 중복 생성 방지

### 페이징 선택 기준
- 무한 스크롤 (모바일 앱): `Slice<T>`
- 관리자 페이지 (번호 페이징): `Page<T>`

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
- `AccountCleanupScheduler` — 매일 03:00, 탈퇴 후 30일 경과 계정 물리 삭제
- `OrderExpirationScheduler` — 1분 주기, 결제 대기(PENDING) 15분 경과 주문 만료 처리
- `NotificationCleanupScheduler` — 매일 03:00 6개월 이전 알림 삭제 / 5분 주기 Redis unread 카운트 ↔ DB 정합성 보정
- `AiScheduledMessageScheduler` — 1분 주기, scheduledAt 경과한 AI 예약 메시지 발송 (최대 50건/회, 재시도 3회 초과 시 FAILED)
- `AiPlanExpirationScheduler` — 매일 03:30, 만료일 지난 AI 플랜 구독 비활성화 (이후 FREE 처리)
- `AiPlanPaymentExpirationScheduler` — 1분 주기, 결제 대기(PENDING) 15분 경과 AI 플랜 결제 FAILED 처리

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
