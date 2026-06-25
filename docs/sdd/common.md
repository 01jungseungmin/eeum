# Common 도메인 SDD (공통 설계)

> 코드 경로: common, config, exception, security, aop, redis, event, scheduler
> 비고: 공통 응답, 에러코드, 보안, 락, 이벤트, 페이징

## 관리자 감사 로그 구현 상태

현재 AdminAuditLog 기능은 SDD 기준 설계만 존재하며, 실제 코드는 아직 구현 전이다.

구현 예정 범위:

- AdminAuditLog Entity
- AdminAuditLogRepository
- AdminAuditLogRepositoryCustom
- AdminAuditService
- AdminAuditAspect
- AdminAudit annotation
- AdminAuditLogController
- 관리자 작업 성공/실패 기록
- 관리자 작업 파라미터 마스킹
- 오래된 감사 로그 정리 스케줄러

이 기능은 특정 도메인에 종속된 기능이 아니라 관리자 작업 전반에 적용되는 횡단 관심사이므로 common.md에서 관리한다.

## 1. 개요

### 1.1 목적

본 문서는 지역 기반 커뮤니티 및 거래 서비스인 "이음" 프로젝트의 소프트웨어 설계를 정의하기 위한 설계 기술서(SDD)이다.
요구사항 명세서(SRS)를 기반으로 시스템의 구조, 구성 요소, 데이터 흐름 및 인터페이스를 구체적으로 정의하여 개발 과정에서 일관된 기준을 제공하는 것을 목적으로 한다.
또한 본 문서는 시스템 설계에 대한 명확한 기준을 제시함으로써 개발 효율성을 향상시키고 유지보수 및 확장성을 고려한 구조 설계를 지원한다.

### 1.2 시스템 개발 범위

본 프로젝트는 지역 기반 커뮤니티 및 거래 서비스를 제공하는 플랫폼 "이음"의 시스템 설계를 목적으로 한다.

● 포함 범위

- 회원 관리 및 인증 (회원가입, 로그인, JWT 기반 인증)
- 활동 지역 설정 및 인증 기능
- 동네 상점 관리 및 상품/주문/결제 기능
- 중고 거래 게시글 및 거래 기능
- 채팅 기능 (거래 및 커뮤니티 연동)
- 커뮤니티 게시판 기능
- 리뷰 및 평점 시스템
- 관리자 기능 (회원 관리, 신고 처리, 상점 승인)
- 알림 기능

● 제외 범위 (확장 기능)

- 카풀 기능 (향후 확장 기능으로 분리)
  본 설계 문서는 위 기능을 중심으로 시스템 구조 및 동작을 정의하며, 확장 기능은 향후 시스템 확장 시 반영할 수 있도록 고려한다.

### 1.3 구성 및 개요

본 문서는 요구사항 명세서(SRS)를 기반으로 시스템의 구조와 동작을 구체적으로 설계하기 위해 작성되었다.

● 개발 환경: 시스템 개발 및 설계에 사용되는 기술 스택과 설계 시 고려사항을 정의한다.
● 시스템 아키텍처 설계: 전체 시스템의 구조와 구성 요소 간 관계를 정의한다.
● 클래스 다이어그램: 도메인 객체 및 클래스 간 관계를 정의한다.
● 시퀀스 다이어그램: 주요 기능의 처리 흐름과 객체 간 상호작용을 정의한다.
● API 설계: 시스템 외부 및 내부 인터페이스를 정의한다.
● 클래스 명세: 각 클래스의 속성 및 책임을 상세히 정의한다.
● 데이터베이스 설계: 데이터 구조 및 테이블 관계를 정의한다.
● 컴포넌트/디플로이먼트 다이어그램: 시스템 구성 요소와 배포 구조를 정의한다.
● 요구사항 추적표: 요구사항과 설계 요소 간의 관계를 명확히 한다.
● 이를 통해 요구사항 → 설계 → 구현으로 이어지는 일관된 개발 흐름을 유지하고, 시스템의 안정성과 확장성을 확보하는 것을 목표로 한다.

## 2. 개발 환경

### 2.1 개발 및 설계 환경/도구

본 프로젝트의 개발 및 설계 환경은 다음과 같다.

● 개발 환경

- Backend: Java, Spring Boot
- Frontend App: React Native
- Frontend Web: React
- Database: MySQL
- Cache: Redis
- Infra Environment: AWS EC2, Docker
- Storage: AWS S3

● 개발 도구

- IDE: IntelliJ IDEA, Visual Studio Code
- 형상관리: Git, GitHub
- 협업 도구: Notion

● 설계 도구

- 다이어그램: PlantUML, Draw.io
- UI 설계: Figma

### 2.2 설계 고려사항

본 시스템은 지역 기반 커뮤니티 및 거래 서비스를 안정적으로 제공하기 위해 다음과 같은 사항을 고려하여 설계하였다.

● 확장성 (Scalability)

- 기능 단위로 도메인을 분리하여 구조를 설계
- 향후 카풀 기능 등 확장 기능 추가를 고려한 구조 적용

● 유지보수성 (Maintainability)

- Controller / Service / Repository 계층 분리
- 역할 기반 설계를 통해 코드 변경 영향 최소화

● 성능 (Performance)

- Redis를 활용한 캐싱 구조 적용
- 조회 중심 기능에 대한 응답 속도 개선 고려
- k6, Grafna 등의 모니터링 도구들을 활용한 성능 개선 고려

● 보안 (Security)

- Spring Security 기반 인증 처리
- JWT를 활용한 Stateless 인증 구조 적용
- 비밀번호 암호화 (BCrypt)

### 2.3 비기능 요구사항 구현 방안

비기능 요구사항을 충족하기 위해 다음과 같은 구현 방안을 적용하였다.

● 인증 및 권한 관리

- Access Token / Refresh Token 구조 적용
- Refresh Token은 Redis에 저장하여 보안성 강화
- 로그아웃 시 토큰 무효화 처리

● 데이터 일관성

- 결제 처리 시 중복 요청 방지를 위한 Redis Lock 적용
- 트랜잭션 관리를 통한 데이터 무결성 유지

● 실시간 통신

- WebSocket 기반 채팅 기능 구현
- 사용자 간 실시간 메시지 전달 지원

● 외부 API 연동 안정성

- 결제 API(PortOne) 연동 시 예외 처리 및 실패 대응 로직 구성
- 외부 API 장애 발생 시 서비스 영향 최소화 고려

● 시스템 안정성

- 예외 처리 로직 표준화
- 주요 기능에 대한 로그 기록 및 모니터링 고려

## 3. 시스템 설계 아키텍처 설계

### 3.1 아키텍처 개요

본 시스템은 계층형 아키텍처(Layered Architecture)를 기반으로 설계되었으며, 각 계층은 역할에 따라 분리되어 유지보수성과 확장성을 고려하였다.
또한 외부 API 및 캐시 시스템을 활용하여 성능과 확장성을 확보하였다.

### 3.2 논리 아키텍처

● 표현 계층 (Presentation Layer)
표현 계층은 사용자 요청을 수신하고 응답을 반환하는 영역이다.
웹 및 모바일 클라이언트로부터 전달된 요청을 Controller가 수신하며, 요청 데이터 검증 및 인증 정보 확인 후 비즈니스 계층으로 전달한다.

● 주요 구성 요소

- React Native 기반 사용자 화면
- 관리자/사장 회원용 웹 화면
- Spring Boot Controller
- 인증 필터(JWT Filter)

● 비즈니스 계층 (Business Layer)
비즈니스 계층은 시스템의 핵심 비즈니스 로직을 처리하는 영역이다.
각 도메인 서비스는 사용자, 상점, 거래, 결제 등 주요 기능에 대한 로직을 수행한다.

주요 역할

- 회원 인증 및 권한 처리
- 지역 기반 서비스 로직 처리
- 상점 및 상품 관리
- 주문 및 결제 처리
- 거래 및 게시글 관리
- 리뷰 및 알림 처리

● 데이터 접근 계층 (Data Access Layer)
데이터 접근 계층은 데이터베이스와의 상호작용을 담당한다.
Repository를 통해 엔티티를 조회, 저장, 수정, 삭제하며, 비즈니스 계층과 데이터 저장 계층 간의 의존성을 분리한다.

● 데이터 저장 계층 (Data Storage Layer)

- MySQL: 주요 영속 데이터 저장
- Redis: 인증 데이터, 캐시, 분산 락 처리
- AWS S3: 이미지 및 파일 데이터 저장

● 외부 연동 계층 (External Integration)
본 시스템은 다음과 같은 외부 서비스와 연동된다.

- PortOne API: 결제 처리
- Kakao Map API: 위치 기반 서비스 제공
- 사업자 인증 API: 사업자 등록 검증
- SMS 인증 API: 사용자 인증
- OAuth Provider: 소셜 로그인 지원

### 3.3 물리 아키텍처

본 시스템은 AWS EC2 환경에서 Docker 기반으로 배포된 Spring Boot 서버로 구성된다. 데이터 저장을 위해 MySQL을 사용하며,
성능 향상 및 토큰 관리, 동시성 제어를 위해 Redis를 활용하였다.
또한 외부 시스템 연동을 통해 결제, 지도, 지역 인증 기능을 제공하며, 이미지 파일 저장을 위해 AWS S3를 사용하였다.

### 3.4 데이터 흐름 개요

사용자의 요청은 Controller를 통해 Service로 전달되며, 비즈니스 로직 처리 후 Repository를 통해 DB에 접근한다.
필요 시 Redis 캐싱 및 외부 API 호출이 수행된다.

### 3.5 설계 의도

- 계층 분리를 통해 유지보수성과 확장성을 확보하였다.
- Redis를 활용하여 조회 성능을 개선 및 토큰 관리를 하였다.
- 외부 API를 분리하여 시스템 결합도를 낮추었다.
- Domain 중심 설계를 통해 비즈니스 로직의 응집도를 높이고, 변경 영향 범위를 최소화하였다.
- 서버 중심 구조로 향후 MSA 확장이 가능하도록 설계하였다.

## 4. 설계 클래스 다이어그램 (개요)

본 항목에서는 시스템을 구성하는 주요 엔티티와 이들 간의 연관 관계를 중심으로 설계 내용을 기술한다.
각 엔티티의 세부 동작 및 메서드 정의는 하위 명세서에서 별도로 기술한다.
공통 속성인 생성일자(createdAt)와 수정일자(modifiedAt)는 BaseEntity를 통해 관리하도록 설계하였다.

> 4장의 DCOM-1 ~ DCOM-11 개별 항목(식별 번호/기능/설계다이어그램/설명)은 각 도메인 파일(account.md, store.md, order-payment.md, used-product.md, community.md, chat.md, notification.md, favorite.md, inquiry.md, sdd-report.md)에 분리하여 기재하였다. DCOM 목록은 다음과 같다.

| 식별 번호 | 기능                              | 분리 위치                                                                             |
| --------- | --------------------------------- | ------------------------------------------------------------------------------------- |
| DCOM-1    | 회원정보 관리                     | account.md                                                                            |
| DCOM-2    | 동네 상점 관리                    | store.md                                                                              |
| DCOM-3    | 동네 상점 예약 및 결제            | order-payment.md, reservation.md                                                      |
| DCOM-4    | 동네 상점 리뷰 및 답글            | store.md                                                                              |
| DCOM-5    | 중고 거래, 중고 거래 리뷰 및 답글 | used-product.md                                                                       |
| DCOM-6    | 커뮤니티                          | community.md                                                                          |
| DCOM-7    | 채팅                              | chat.md                                                                               |
| DCOM-8    | 알림                              | notification.md                                                                       |
| DCOM-9    | 찜                                | favorite.md                                                                           |
| DCOM-10   | 문의                              | inquiry.md                                                                            |
| DCOM-11   | 신고                              | sdd-report.md (원문 파일명은 report.md이나 작업 환경 제약으로 sdd-report.md로 저장됨) |

[다이어그램 원본은 기존 SDD 문서 참조]

## 6. 공통 설계 패턴

### 6.1 동시성 제어 전략 (Concurrency Control Strategy)

이음 시스템은 다수의 사용자가 동시에 동일 자원(재고, 주문, 예약 등)에 접근하는 구조를 가진다.
특히 재고 차감, 결제 처리, 검증 등과 같이 데이터 정합성이 중요한 영역에서는 오류 발생 시 비즈니스에 직접적인 영향을 미친다.
이에 따라 본 시스템은 상황에 맞는 최적의 동시성 제어를 적용하기 위해 다층적 동시성 제어 전략(Multi-layer Concurrency Control)을 도입한다.

#### 6.1.1 동시성 제어 4계층

| 계층 | 메커니즘                           | 사용 시나리오                                         |
| ---- | ---------------------------------- | ----------------------------------------------------- |
| 1    | Redis 분산 락                      | 분산 환경 동시 진입 차단 (예약 capacity, 결제 멱등성) |
| 2    | 비관적 락 @Lock(PESSIMISTIC_WRITE) | 재고 차감, 결제 상태 변경, 거래 상태 전이             |
| 3    | 낙관적 락 @Version                 | 평점 갱신, 카운트 갱신                                |
| 4    | DB 원자 UPDATE                     | favoriteCount, viewCount 등 단순 카운트               |

#### 6.1.2 Redis 분산 락 명명 규칙

분산 환경에서 동일 자원에 대한 동시 접근을 제어하기 위해 Redis 기반 분산 락을 사용하며, 락 키는 도메인 및 목적에 따라 일관된 규칙으로 정의한다.

| Key Pattern                              | TTL | 설명                                         |
| ---------------------------------------- | --- | -------------------------------------------- |
| order:product:{productId}                | 3s  | 주문 시 재고 차감 (단일 상품)                |
| order:products:{1-3-5}                   | 3s  | 다중 상품 (productIds 오름차순 정렬 후 결합) |
| payment:order:{orderId}                  | 10s | 결제 처리 시 중복 방지                       |
| payment:webhook:{portOnePaymentId}       | 30s | Webhook 중복 수신 방지                       |
| reservation:{storeId}:{yyyy-MM-dd-HH-mm} | 3s  | 매장 방문 예약 (분 단위)                     |
| chat:private:{refType}:{refId}           | 5s  | 1:1 채팅방 멱등 생성                         |
| used-product:trade:{usedproductId}       | 5s  | 중고거래 상태 변경                           |

● 데드락 방지 전략
다중 상품 주문 시, 여러 상품에 대한 락 획득 순서가 달라질 경우 데드락이 발생 가능하다.
이를 방지하기 위해 productId를 오름차순으로 정렬한 후 단일 키로 결합하여 사용한다.
예시: [3, 1, 5] → 1-3-5

#### 6.1.3 비관적 락 적용 위치

데이터 충돌 가능성이 높고, 반드시 순차 처리가 필요한 경우 비관적 락을 적용한다.

| Repository             | 메서드                           | 용도              |
| ---------------------- | -------------------------------- | ----------------- |
| ProductRepository      | findByIdForUpdate(productId)     | 재고 차감         |
| EventProductRepository | findActiveByProductIdForUpdate   | 예약 확정 및 취소 |
| OrderRepository        | findByIdForUpdate(orderId)       | 결제 처리 및 취소 |
| ReservationRepository  | findByIdForUpdate(reservationId) | 예약 확정 및 취소 |
| UsedProductRepository  | findByIdForUpdate(usedproductId) | 거래 상태 변경    |

#### 6.1.4 낙관적 락 적용

충돌 가능성이 상대적으로 낮고, 성능이 우선인 경우 낙관적 락을 적용한다.

● 적용 대상 Entity

- Store
- Product
- EventProduct
- Orders
- Reservation
- UsedProduct

각 엔티티는 JPA의 @Version 컬럼을 통해 버전 기반 충돌 검증을 수행한다.

● 재시도 처리 전략
낙관적 락 충돌 발생 시, 사용자 경험 저하를 최소화하기 위해 자동 재시도 정책을 적용한다.

- 최대 재시도 횟수: 3회
- 초기 대기 시간: 100ms
- 백오프 전략: 지수 증가 (multiplier = 2)

● 적용 방식

```java
@Retryable(
    value = ObjectOptimisticLockingFailureException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 100, multiplier = 2)
)
@Transactional
public void updateRating(Long storeId, double newRating) {
    ...
}
```

#### 6.1.5 DB 원자 연산 (Atomic UPDATE)

조회수, 찜 수 등 단순 카운트 데이터는 락을 사용하지 않고 DB의 원자적 UPDATE 연산을 통해 처리한다.

● 적용 목적

- 락 사용 없이 성능 확보
- 동시성 문제 최소화
- 트랜잭션 비용 감소

● 구현 방식

```java
@Modifying
@Query("UPDATE Store s SET s.favoriteCount = s.favoriteCount + 1 WHERE s.storeId = :storeId")
int incrementFavoriteCount(@Param("storeId") Long storeId);
```

● 음수 방지 처리

```java
@Modifying
@Query("""
UPDATE Store s
SET s.favoriteCount = s.favoriteCount - 1
WHERE s.storeId = :storeId AND s.favoriteCount > 0
""")
int decrementFavoriteCount(@Param("storeId") Long storeId);
```

● 설계 의도

- 단순 카운트는 DB 레벨에서 직접 처리하여 성능 최적화
- 조건절을 통해 데이터 무결성 보장 (음수 방지)

### 6.2 이벤트 기반 아키텍처 (Event-Driven Architecture)

도메인 간 결합도를 낮추고, 알림·통계·캐시 갱신과 같은 부가 기능과 핵심 비즈니스 로직의 분리를 위해 Spring의 ApplicationEventPublisher를 활용한 이벤트 기반 아키텍처(Event-Driven Architecture)를 적용한다.
이를 통해 서비스 계층은 핵심 비즈니스 로직에 집중하고, 부가 기능은 이벤트 리스너에서 독립적으로 처리한다.

#### 6.2.1 이벤트 발행 원칙

발행 위치: 도메인 이벤트는 Service 계층에서 발행한다.
제한 사항: Entity 내부에서는 이벤트를 발행하지 않는다.

#### 6.2.2 이벤트 명명 규칙

이벤트는 발생 시점과 의미를 명확히 표현하기 위해 다음과 같은 네이밍 규칙을 따른다.

| 패턴              | 예시                      | 발행 시점        |
| ----------------- | ------------------------- | ---------------- |
| XxxCreatedEvent   | OrderCreatedEvent         | 엔티티 생성 직후 |
| XxxConfirmedEvent | ReservationConfirmedEvent | 상태 전이 직후   |
| XxxCancelledEvent | OrderCancelledEvent       | 취소 처리 후     |
| XxxCompletedEvent | PaymentCompletedEvent     | 완료 처리 후     |

#### 6.2.3 트랜잭션 동기화 전략

이벤트 리스너는 트랜잭션의 상태에 따라 실행 시점을 제어한다.

| Phase            | 사용 사례               | 특징                                               |
| ---------------- | ----------------------- | -------------------------------------------------- |
| AFTER_COMMIT     | 알림, 푸시, 이메일 발송 | 트랜잭션 커밋 후 실행 → 롤백 시 실행되지 않음      |
| BEFORE_COMMIT    | 감사 로그 기록          | REQUIRES_NEW와 함께 사용하여 실패 시에도 기록 유지 |
| AFTER_COMPLETION | 락 해제, 리소스 정리    | 성공/실패 여부와 관계없이 실행                     |

● 필수 원칙
모든 알림, 푸시, 이메일 관련 이벤트 리스너는 반드시 @TransactionalEventListener(phase = AFTER_COMMIT)을 사용한다.
트랜잭션이 롤백된 경우, 사용자에게 잘못된 알림이 발송되는 것을 방지

#### 6.2.4 비동기 이벤트 처리

이벤트 리스너는 @Async를 활용하여 비동기적으로 처리한다.
이를 통해 메인 트랜잭션의 응답 시간을 최소화한다.

● 적용 예시

```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onMessageSent(ChatMessageSentEvent event) {
    // 1. ACTIVE 참여자 조회
    // 2. unread:account:{accountId} INCR
    // 3. 비활성 참여자에게 FCM 푸시
}
```

● 설계 의도

- 이벤트 처리 로직을 비동기화하여 응답 속도 개선
- 알림, 카운트 증가 등 부가 기능을 메인 로직과 분리
- 시스템 확장 시 이벤트 소비자만 추가하면 되도록 구조 설계

#### 6.2.5 주요 이벤트 카탈로그

시스템에서 발생하는 주요 이벤트와 처리 내용을 다음과 같이 정의한다.

| 이벤트                | 발행 위치                           | 처리 내용                       |
| --------------------- | ----------------------------------- | ------------------------------- |
| OrderCreatedEvent     | OrderService.createOrder            | 사장에게 신규 주문 알림         |
| OrderConfirmedEvent   | OwnerOrderService.confirmOrder      | 사용자에게 주문 확정 알림       |
| OrderCancelledEvent   | OrderService.cancelOrder            | 결제 취소 및 재고 복구          |
| PaymentCompletedEvent | PaymentService.handleWebhook        | 주문 상태를 PAID로 전이         |
| ChatMessageSentEvent  | ChatMessageService.sendMessage      | 참여자 unread 증가 및 푸시 발송 |
| ReviewCreatedEvent    | StoreReviewService.registerReview   | 평점 재계산 및 사장 알림        |
| OwnerApprovedEvent    | AdminAccountService.approveOwner    | 사장 승인 알림 및 권한 변경     |
| PostLikedEvent        | CommunityPostService.togglePostLike | 게시글 작성자 알림              |
| InquiryAnsweredEvent  | OwnerInquiryService.answerInquiry   | 문의 답변 알림                  |

### 6.3 CQRS 패턴 적용 (Command Query Responsibility Segregation)

완전한 CQRS(별도 DB 분리)는 적용하지 않지만, Service 계층에서 Command(쓰기)와 Query(읽기)를 명확히 분리한 경량 CQRS 패턴을 적용한다.

#### 6.3.1 Command / Query 분리 원칙

| 구분          | Command (쓰기)                                | Query (읽기)                    |
| ------------- | --------------------------------------------- | ------------------------------- |
| 메서드 Prefix | create / update / delete / register / confirm | get / find / search / list      |
| 트랜잭션      | @Transactional (REQUIRED)                     | @Transactional(readOnly = true) |
| 반환 타입     | Entity → ResponseDto 변환                     | QueryDSL Projection DTO         |
| 부수 효과     | 상태 변경, 이벤트 발행                        | 없음                            |
| 캐싱 전략     | @CacheEvict                                   | @Cacheable                      |
| DB 라우팅     | Master DB                                     | Slave DB (Replication 시)       |

#### 6.3.2 readOnly 트랜잭션의 이점

읽기 전용 트랜잭션은 다음과 같은 성능 최적화 효과를 제공한다.

● Hibernate Dirty Checking 비활성화

- 영속성 컨텍스트 스냅샷 비교 생략 → 성능 향상

● Flush Mode MANUAL 적용

- 자동 flush 방지 → 불필요한 UPDATE 차단

● DB Connection 라우팅 가능

- ReplicationRoutingDataSource를 통해 Slave DB로 분산 처리
  문서화 효과 → 메서드 시그니처만으로 읽기/쓰기 구분 가능

#### 6.3.3 QueryDSL Projection 활용

목록 조회 시 Entity 전체를 조회하지 않고, 필요한 필드만 DTO로 직접 Projection하여 조회한다.

● 적용 예시

```java
List<StoreListResponseDto> content = queryFactory
    .select(Projections.fields(StoreListResponseDto.class,
        s.storeId, s.name, s.rating, s.favoriteCount, s.status,
        si.imageUrl.as("thumbnailUrl")))
    .from(s)
    .leftJoin(si).on(si.store.eq(s).and(si.isThumbnail.isTrue()))
    .where(
        regionEq(cond.getRegionId()),
        categoryEq(cond.getCategoryId()),
        keywordContains(cond.getKeyword()),
        s.status.eq(StoreStatus.OPEN)
    )
    .orderBy(orderSpec(cond.getSort()))
    .offset(pageable.getOffset())
    .limit(pageable.getPageSize())
    .fetch();
```

● 설계 의도

- 필요한 데이터만 조회하여 성능 최적화
- Entity 로딩 비용 감소
- API 응답 속도 개선

● N+1 문제 해결
목록 조회 시 대표 이미지를 가져오는 과정에서 발생하는 OneToMany Lazy Loading 기반 N+1 문제를 다음과 같이 해결한다.

- Projection + LEFT JOIN을 사용하여 단일 쿼리로 처리
- 추가 쿼리 발생 방지

### 6.4 캐싱 전략 (Caching Strategy)

응답 시간 단축, DB 부하 감소, 동시성 제어를 위해 Redis를 활용한 다목적 캐싱 전략을 적용한다.

#### 6.4.1 캐싱 카탈로그

| 용도               | Key 패턴                              | TTL  | 갱신 시점                   |
| ------------------ | ------------------------------------- | ---- | --------------------------- |
| 카테고리 트리      | category:tree:{type}                  | 1h   | 관리자 변경 시 CacheEvict   |
| 상점 상세          | store:detail:{storeId}                | 10m  | 상점 정보 수정 / 평점 변경  |
| 사용자 unread 알림 | unread:account:{accountId}            | 영구 | 알림 생성/읽음 시 INCR/DECR |
| 전체 채팅 unread   | unread:chat:{accountId}               | 영구 | 메시지 송수신 시            |
| 방별 채팅 unread   | unread:chat:{accountId}:room:{roomId} | 영구 | 동일                        |
| 게시글 조회수      | view:post:{postId}                    | 5m   | Redis 누적 후 배치 동기화   |
| 상품 조회수        | view:product:{productId}              | 5m   | 동일                        |
| 중고상품 조회수    | view:used:{usedProductId}             | 5m   | 동일                        |
| 사장 대시보드      | dashboard:store:{storeId}             | 1m   | 주문/리뷰 발생 시 evict     |

#### 6.4.2 Cache-Aside 패턴

조회 시 캐시를 먼저 확인하고, 없을 경우 DB 조회 후 캐시에 저장한다.

● 적용 예시

```java
@Cacheable(value = "categoryTree", key = "#type", unless = "#result == null")
@Transactional(readOnly = true)
public List<CategoryTreeResponseDto> getCategoryTree(CategoryType type) {
    List<Category> categories = categoryRepository.findAllByTypeAndIsActiveTrue(type);
    return buildTree(categories);
}
@CacheEvict(value = "categoryTree", key = "#dto.type")
@Transactional
public CategoryResponseDto createCategory(CategoryCreateRequestDto dto) {
    ...
}
```

#### 6.4.3 Write-Through 패턴 (카운터 동기화)

조회수와 같이 쓰기 빈도가 높고, 약한 정합성을 허용하는 데이터는 Redis에서 먼저 처리한 후 배치로 DB에 반영한다.

● 조회 시 (Redis INCR)

```java
public void incrementViewCount(Long postId) {
    redisTemplate.opsForValue().increment("view:post:" + postId);
}
```

● 배치 동기화 (Redis → DB)

```java
@Scheduled(fixedRate = 300_000)
public void syncViewCounts() {
    Set<String> keys = redisTemplate.keys("view:post:*");

    for (String key : keys) {
        Long postId = extractId(key);
        Integer increment = redisTemplate.opsForValue().get(key);

        if (increment > 0) {
            communityPostRepository.addViewCount(postId, increment);
            redisTemplate.delete(key);
        }
    }
}
```

#### 6.4.4 캐시 정합성 보정

unread 카운트는 Redis 기반으로 관리되며, 주기적으로 DB와 비교하여 정합성을 보정한다.

● 실행 주기: 5분
● 처리 컴포넌트: ChatUnreadReconcileScheduler
● 처리 방식: DB 기준 재계산 후 Redis 갱신

#### 6.4.5 캐시 무효화 규칙

| 전략             | 설명                                                     |
| ---------------- | -------------------------------------------------------- |
| 즉시 무효화      | 카테고리, 상점 정보 변경 시 즉시 CacheEvict              |
| TTL 기반 만료    | 통계, 대시보드 등 일부 stale 허용                        |
| 부분 무효화      | 특정 사용자만 캐시 제거 (예: unread:account:{accountId}) |
| 전체 무효화 금지 | @CacheEvict(allEntries = true) 운영 환경 사용 금지       |

### 6.5 멱등성 보장 (Idempotency)

동일 요청이 여러 번 전달되더라도 동일한 결과를 보장하기 위해 멱등성(Idempotency) 설계를 적용한다.
이를 통해 네트워크 재시도, Webhook 재전송, 사용자 중복 클릭 등으로 인한 데이터 중복 생성 및 정합성 오류를 방지한다.

#### 6.5.1 멱등성 적용 시나리오

| 시나리오        | 발생 원인                    | 보장 방식                         |
| --------------- | ---------------------------- | --------------------------------- |
| 결제 요청       | 중복 클릭, 네트워크 재시도   | Idempotency-Key + Redis           |
| PortOne Webhook | PG사 재전송 정책             | portOnePaymentId UNIQUE 제약      |
| 1:1 채팅방 생성 | 동일 거래에서 중복 채팅 시작 | (refType, refId, type) UNIQUE     |
| 좋아요 토글     | 빠른 중복 탭                 | (accountId, postId) UNIQUE + 토글 |
| 주문 생성       | 중복 제출                    | 주문번호 + Idempotency-Key        |

#### 6.5.2 Idempotency-Key 패턴

클라이언트에서 생성한 고유 키를 기반으로 서버에서 중복 요청을 제어한다.

● 처리 흐름

1. Client → UUID 생성 후 HTTP Header에 포함
   POST /payments
   Idempotency-Key: 7f8e9d2a-...

2. Server → Redis 조회
   GET idempotency:payment:{key}

   ┌ Cache HIT → 기존 응답 반환
   └ Cache MISS → 정상 처리 진행

3. 처리 완료 후 Redis 저장 (TTL 24h)

4. 동일 요청 재시도 시 캐시된 응답 반환

● 설계 의도

- 네트워크 재시도 상황에서도 동일 결과 보장
- 결제 중복 처리 방지
- 클라이언트-서버 간 멱등성 협력 구조 구현

#### 6.5.3 DB UNIQUE 제약 기반 멱등성

Webhook과 같이 클라이언트가 멱등 키를 제어할 수 없는 경우, 비즈니스 키에 UNIQUE 제약을 적용하여 중복 처리를 방지한다.

● 적용 예시

```java
@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = "portOnePaymentId"),
    @UniqueConstraint(columnNames = "idempotencyKey")
})
public class Payment { ... }

@Transactional
public void handleWebhook(WebhookRequestDto dto) {
    if (paymentRepository.existsByPortOnePaymentId(dto.getPortOnePaymentId())) {
        log.info("이미 처리된 Webhook: {}", dto.getPortOnePaymentId());
        return;
    }
    // 정상 처리...
}
```

● 설계 의도

- 외부 시스템(Webhook) 재전송에 대한 방어
- DB 레벨에서 최종 중복 방지
- 애플리케이션 로직과 DB 제약의 이중 안전장치

#### 6.5.4 토글 작업 멱등성

좋아요/찜과 같은 토글 기능은 "현재 상태와 관계없이 결과 상태가 결정"되도록 설계한다.

● 적용 예시

```java
public FavoriteToggleResponseDto toggleFavorite(Long accountId, FavoriteToggleRequestDto dto) {
    Optional<Favorite> existing = favoriteRepository
        .findByAccountIdAndRefTypeAndRefId(accountId, dto.getRefType(), dto.getRefId());

    if (existing.isPresent()) {
        favoriteRepository.delete(existing.get());
        return new FavoriteToggleResponseDto(false);
    } else {
        favoriteRepository.save(new Favorite(accountId, dto.getRefType(), dto.getRefId()));
        return new FavoriteToggleResponseDto(true);
    }
}
```

● 설계 의도

- 중복 클릭 시에도 상태 일관성 유지
- UNIQUE 제약과 결합하여 데이터 무결성 보장

### 6.6 삭제 정책 (Deletion Policy)

도메인 특성에 따라 Soft Delete와 Hard Delete를 구분하여 적용한다.
무분별한 Soft Delete 사용은 쿼리 복잡도 증가 및 저장 공간 낭비를 초래할 수 있다.

#### 6.6.1 삭제 정책 결정 기준

| 판단 기준   | Soft Delete | Hard Delete |
| ----------- | ----------- | ----------- |
| 복구 필요성 | 복구 필요   | 복구 불필요 |
| 법적 보존   | 필요        | 불필요      |
| 참조 무결성 | 참조 많음   | 독립적      |
| 통계 목적   | 유지 필요   | 영향 없음   |

#### 6.6.2 도메인별 삭제 정책

| 도메인          | 정책                      | 적용 이유                    |
| --------------- | ------------------------- | ---------------------------- |
| Account         | Soft Delete (30일 유예)   | 복구 + 법적 보존 + 다수 참조 |
| ChatMessage     | Soft Delete (isDeleted)   | 대화 맥락 유지               |
| Category        | Soft Delete (isActive)    | 참조 데이터 보호             |
| ProductCategory | Soft + 조건부 Hard Delete | 미사용 시 물리 삭제          |
| 기타 도메인     | Hard Delete + CASCADE     | 단순 데이터                  |

#### 6.6.3 Account Soft Delete 흐름

```
1. 사용자 탈퇴 요청
   ↓ Re-auth 검증
   ↓ status = WITHDRAWN, deletedAt 설정
   ↓ Refresh Token 삭제
2. 30일 이내 복구 가능
   → status = ACTIVE
3. 30일 이후 자동 삭제
   → Scheduler 수행
   → CASCADE 정리
```

#### 6.6.4 ChatMessage Soft Delete

메시지 삭제 시 실제 데이터는 유지하고 상태만 변경한다.

● 적용 예시

```java
@Entity
public class ChatMessage {
    private boolean isDeleted = false;

    public void markDeleted() {
        this.isDeleted = true;
    }
}
if (msg.isDeleted()) {
    return new ChatMessageResponseDto(
        msg.getChatmessageId(),
        "삭제된 메시지입니다",
        null,
        msg.getMessageType(),
        msg.getSentAt()
    );
}
```

● 설계 의도

- 대화 흐름 유지
- 신고 및 감사 로그 대응 가능

#### 6.6.5 Category 삭제 전략

기본적으로 Soft Delete를 적용하며, 참조 데이터가 없는 경우에만 Hard Delete를 허용한다.

● 적용 예시

```java
public void deactivateCategory(Long categoryId) {
    category.deactivate();
}
public void hardDeleteCategory(Long categoryId) {
    if (참조 존재) throw Exception;
    categoryRepository.deleteById(categoryId);
}
```

● 설계 의도

- 참조 무결성 유지
- 안전한 삭제 정책 적용

#### 6.6.6 Hard Delete + CASCADE 정책

| 트리거      | 대상 도메인                  | 처리 방식                  |
| ----------- | ---------------------------- | -------------------------- |
| 회원 삭제   | Favorite, Notification, Cart | deleteAllByAccountId       |
| 상점 삭제   | Product, Image               | deleteAllByStoreId         |
| 상품 삭제   | Option, Image                | deleteAllByProductId       |
| 리뷰 삭제   | Reply, Image                 | deleteAllByReviewId        |
| 게시글 삭제 | Comment, Like                | deleteAllByPostId          |
| 댓글 삭제   | Reply                        | deleteAllByCommentId       |
| 채팅방 삭제 | Message, Participant         | deleteAllByRoomId          |
| 공통 삭제   | Favorite, Report             | deleteAllByRefTypeAndRefId |
| 기타 도메인 | Hard Delete + CASCADE        | 단순 데이터                |

### 6.7 AOP 기반 횡단 관심사 분리

로깅, 권한 검증, 감사 기록, 캐시 처리와 같은 비즈니스 로직과 직접 관련 없는 공통 기능을 AOP(Aspect-Oriented Programming)로 분리한다.
이를 통해 핵심 비즈니스 로직의 복잡도를 낮추고, 공통 기능을 중앙에서 일관되게 관리할 수 있도록 한다.

#### 6.7.1 AOP 적용 영역

| 관심사    | 어노테이션             | 구현 클래스           | 역할                              |
| --------- | ---------------------- | --------------------- | --------------------------------- |
| 감사 로그 | @AdminAudit            | AdminAuditAspect      | 관리자 작업 자동 기록             |
| 권한 검증 | @OwnerOnly, @AdminOnly | AuthorizationAspect   | 사용자 권한 검증                  |
| 분산 락   | @DistributedLock       | DistributedLockAspect | Redis 기반 락 제어                |
| 멱등성    | @Idempotent            | IdempotencyAspect     | 중복 요청 방지                    |
| 재시도    | @Retryable             | Spring Retry          | 외부 API 실패 및 낙관적 락 재시도 |
| 성능 측정 | @PerformanceLog        | PerformanceLogAspect  | 실행 시간 로깅                    |

#### 6.7.2 @AdminAudit — 감사 로그 자동화

● 어노테이션 정의

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminAudit {
    AuditActionType value();
    String targetType() default "";
    String targetIdParam() default "";
}
```

● Aspect 구현

```java
@Aspect
@Component
@RequiredArgsConstructor
public class AdminAuditAspect {

    private final AdminAuditService auditService;

    @Around("@annotation(adminAudit)")
    public Object log(ProceedingJoinPoint joinPoint, AdminAudit adminAudit) throws Throwable {

        Long adminId = SecurityUtil.getCurrentAccountId();
        String parameters = serializeArgs(joinPoint.getArgs());
        Long targetId = extractTargetId(joinPoint, adminAudit.targetIdParam());

        try {
            Object result = joinPoint.proceed();

            auditService.recordAction(AuditActionDto.success(
                adminId,
                adminAudit.value(),
                adminAudit.targetType(),
                targetId,
                parameters
            ));

            return result;

        } catch (Throwable e) {

            auditService.recordAction(AuditActionDto.failure(
                adminId,
                adminAudit.value(),
                adminAudit.targetType(),
                targetId,
                parameters,
                e.getMessage()
            ));

            throw e;
        }
    }
}
```

● 사용 예시

```java
@AdminAudit(
    value = AuditActionType.ACCOUNT_SUSPEND,
    targetType = "ACCOUNT",
    targetIdParam = "accountId"
)
@Transactional
public void suspendAccount(Long accountId) {
    Account account = accountRepository.findById(accountId).orElseThrow();
    account.suspend();
}
```

#### 6.7.3 @DistributedLock — 분산 락 처리

Redis 기반 분산 락을 AOP로 구현해 동시성 제어를 공통 로직으로 분리한다.

● 어노테이션 정의

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    String key();
    long waitTime() default 3000;
    long leaseTime() default 3000;
}
```

● Aspect 구현

```java
@Aspect
@Component
@Order(1)  // @Transactional보다 먼저 실행
public class DistributedLockAspect {

    private final RedissonClient redissonClient;

    @Around("@annotation(distributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {

        String key = parseSpEL(distributedLock.key(), joinPoint);
        RLock lock = redissonClient.getLock(key);

        boolean acquired = lock.tryLock(
            distributedLock.waitTime(),
            distributedLock.leaseTime(),
            MILLISECONDS
        );

        if (!acquired) {
            throw new BusinessException("동시 접근 실패");
        }

        try {
            return joinPoint.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

● 설계 원칙

- 분산 락은 반드시 트랜잭션 시작 전에 획득
- @Order(1)을 통해 @Transactional보다 먼저 실행
- 트랜잭션 내부에서 락을 획득하면 커밋 전에 해제되어 무력화될 수 있음

#### 6.7.4 AOP 실행 순서

AOP는 실행 순서에 따라 동작이 달라지므로 명확한 우선순위를 정의한다.

| 순서 | Aspect                | 이유                      |
| ---- | --------------------- | ------------------------- |
| 1    | DistributedLockAspect | 트랜잭션 이전에 락 획득   |
| 2    | IdempotencyAspect     | 중복 요청 사전 차단       |
| 3    | AdminAuditAspect      | 트랜잭션 외부에서 기록    |
| 4    | @Transactional        | Spring 기본 트랜잭션 처리 |
| 5    | AuthorizationAspect   | 트랜잭션 내부 권한 검증   |
| 6    | PerformanceLogAspect  | 실제 실행 시간 측정       |

### 6.8 트랜잭션 전파 전략

이음 시스템은 Spring의 @Transactional 전파 속성을 명시적으로 제어하여 트랜잭션 경계를 명확하게 관리한다.

#### 6.8.1 전파 속성 사용 원칙

| 전파 속성       | 사용 사례            | 예시                       |
| --------------- | -------------------- | -------------------------- |
| REQUIRED (기본) | 일반 비즈니스 로직   | createOrder, updateProduct |
| REQUIRES_NEW    | 별도 트랜잭션 필요   | 감사 로그 기록             |
| NESTED          | 부분 롤백 처리       | 배치 처리                  |
| MANDATORY       | 반드시 트랜잭션 필요 | 내부 헬퍼 메서드           |

#### 6.8.2 REQUIRES_NEW 적용

감사 로그와 같이 본 작업과 독립적으로 처리해야 하는 경우 별도의 트랜잭션으로 실행한다.

● 적용 예시

```java
@Service
public class AdminAccountService {

    @Transactional
    public void suspendAccount(Long accountId) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.suspend();
    }
}
@Service
public class AdminAuditService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAction(AuditActionDto dto) {
        adminAuditLogRepository.save(AdminAuditLog.from(dto));
    }
}
```

● 설계 의도

- 본 작업 실패 여부와 관계없이 감사 로그 유지
- 트랜잭션 분리를 통한 안정성 확보

#### 6.8.3 readOnly 트랜잭션 활용

읽기 전용 로직에는 readOnly 옵션을 적용하여 성능을 최적화한다.

● 적용 예시

```java
@Service
public class StoreService {

    @Transactional(readOnly = true)
    public Page<StoreResponseDto> getStoreList(...) { ... }

    @Transactional(readOnly = true)
    public StoreDetailResponseDto getStoreDetail(...) { ... }

    @Transactional
    public void updateRating(...) { ... }
}
```

#### 6.8.4 트랜잭션 설계 가이드라인

● Service 계층을 트랜잭션 경계로 설정 (Controller / Repository는 제외)
● Self-invocation 주의 (내부 호출 시 트랜잭션 적용 안 됨)
● 외부 API 호출은 트랜잭션 외부에서 처리 (이벤트 + @Async 활용)
● 롤백 대상 명시 (rollbackFor = Exception.class)
● 장시간 처리 로직은 timeout 설정 권장 (@Transactional(timeout = 30))

### 6.9 외부 시스템 연동 패턴

PortOne, FCM, AWS S3, OAuth, 사업자번호 검증 API 등 다수의 외부 시스템과 연동된다.
외부 시스템 장애가 내부 시스템으로 전파되지 않도록 격리(Isolation) 및 안정성 확보 전략을 적용한다.

#### 6.9.1 외부 시스템 격리 전략

| 외부 시스템       | 용도                | 격리 전략                       |
| ----------------- | ------------------- | ------------------------------- |
| PortOne           | 결제, 환불, Webhook | Webhook 서명 검증 + 멱등성 키   |
| FCM               | 푸시 알림           | PushAdapter 인터페이스 + @Async |
| AWS S3            | 이미지 저장         | Presigned URL 방식              |
| Kakao/Naver OAuth | 소셜 로그인         | TokenService 캡슐화             |
| 사업자번호 API    | 사업자 검증         | @Retryable + Circuit Breaker    |
| KakaoMap API      | 지도/좌표 변환      | 클라이언트 직접 호출            |

#### 6.9.2 PortOne Webhook 보안 처리

외부 결제 시스템(Webhook)은 신뢰할 수 없는 요청이므로 보안 검증 및 멱등성 처리를 반드시 수행한다.

● 처리 흐름

```
1. PortOne → POST /payments/webhook
   Headers: x-portone-signature, x-portone-timestamp

2. WebhookSignatureFilter
   - timestamp ±5분 검증 (리플레이 공격 방지)
   - HMAC-SHA256 서명 검증
   - 실패 시 401 반환

3. SecurityConfig
   - /payments/webhook → JWT 인증 제외

4. PaymentService.handleWebhook()
   - portOnePaymentId UNIQUE 검증 (중복 방지)
   - 결제 금액 검증
   - 주문 상태 변경

5. 200 OK 응답 (재전송 방지)
```

#### 6.9.3 FCM 푸시 — Adapter 패턴

외부 API 의존성을 줄이기 위해 Adapter 패턴을 적용한다.

● 인터페이스

```java
public interface PushAdapter {
    PushResult send(PushMessage message);
    List<PushResult> sendBatch(List<PushMessage> messages);
}
```

● 구현체

```java
@Component
public class FcmPushAdapter implements PushAdapter {

    @Override
    public PushResult send(PushMessage message) {
        try {
            firebaseMessaging.send(buildMessage(message));
            return PushResult.SUCCESS;
        } catch (FirebaseMessagingException e) {
            if (isInvalidToken(e)) {
                handleInvalidToken(message.getFcmToken());
                return PushResult.INVALID_TOKEN;
            }
            return PushResult.FAILED;
        }
    }
}
```

● 설계 의도

- 외부 API 변경 시 영향 최소화
- 테스트 및 Mocking 용이
- 장애 대응 로직 캡슐화

#### 6.9.4 S3 Presigned URL 패턴

이미지 업로드 시 서버를 거치지 않고 클라이언트가 S3에 직접 업로드하도록 설계한다.

● 처리 흐름

```
1. Client → Server: Presigned URL 요청
2. Server → URL 생성 (TTL 5분)
3. Client → S3 직접 업로드
4. Client → Server: imageUrl 전달
5. Server → S3 객체 존재 검증 후 DB 저장
```

● 설계 의도

- 서버 부하 감소
- 업로드 성능 향상
- 대용량 파일 처리 효율화

#### 6.9.5 Circuit Breaker 패턴

외부 API 장애 시 시스템 전체 장애로 확산되는 것을 방지한다.

● 적용 예시

```java
@CircuitBreaker(name = "businessApi", fallbackMethod = "fallback")
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
public boolean validate(String businessNumber) {
    return externalApi.checkBusinessNumber(businessNumber);
}
```

● Fallback 전략

```java
public boolean fallback(String businessNumber, Throwable e) {
    log.warn("API 장애, 임시 허용");
    return true; // PENDING 상태로 처리
}
```

● 설계 의도

- 외부 장애 전파 차단
- 서비스 지속성 확보
- 관리자 검증으로 후처리

### 6.10 보안 및 인가 패턴

사용자 권한을 기반으로 접근을 제어하며, 민감 작업에 대해서는 추가 검증을 수행한다.

#### 6.10.1 권한 등급

| Role       | 권한        | URL         |
| ---------- | ----------- | ----------- |
| ROLE_USER  | 일반 사용자 | 기본 API    |
| ROLE_OWNER | 사장        | /owner/\*\* |
| ROLE_ADMIN | 관리자      | /admin/\*\* |

#### 6.10.2 Spring Security 권한 설정

```java
http
    .authorizeHttpRequests(auth -> auth
        .antMatchers("/auth/**").permitAll()
        .antMatchers("/payments/webhook").permitAll()
        .antMatchers("/admin/**").hasRole("ADMIN")
        .antMatchers("/owner/**").hasRole("OWNER")
        .anyRequest().authenticated())
```

#### 6.10.3 다단계 검증 패턴

단순 URL 권한 검증을 넘어 추가 검증을 수행한다.

● 검증 흐름

1. ROLE 확인
2. accountId 추출
3. 소유권 검증
4. 상태 검증
5. 비즈니스 로직 실행

#### 6.10.4 재인증 (Re-authentication)

민감 작업은 별도의 재인증 토큰을 요구한다.

● 처리 흐름

1. 요청 → REAUTH_REQUIRED
2. 비밀번호 검증
3. Redis에 토큰 저장 (TTL 5분)
4. 요청 재시도
5. 1회 사용 후 삭제

#### 6.10.5 민감 정보 마스킹

| 항목     | 원본           | 마스킹             |
| -------- | -------------- | ------------------ |
| 이메일   | hong@gmail.com | ho\*\*\*@gmail.com |
| 전화번호 | 010-1234-5678  | 010-\*\*\*\*-5678  |
| 이름     | 홍길동         | 홍\*동             |

#### 6.10.6 JWT 토큰 정책

| 토큰            | TTL                  | 저장 위치        | 용도             |
| --------------- | -------------------- | ---------------- | ---------------- |
| Access Token    | 30분                 | 클라이언트       | 인증             |
| Refresh Token   | 14일                 | Redis            | 재발급           |
| Re-auth Token   | (원문 TTL 표기 누락) | (원문 표기 누락) | 민감 작업 재인증 |
| 이메일 인증     | 3분                  | Redis            | 회원가입         |
| 비밀번호 재설정 | 10분                 | Redis            | 비밀번호 재설정  |

> 참고: 원문 표(6.10.6)에서 "Re-auth Token" 행의 TTL/저장 위치 셀이 줄바꿈으로 분리되어 명확하게 채워지지 않은 상태로 기재되어 있다(원문에 "Re-auth Token" 다음 줄에 빈 칸들이 이어지고, 그 다음 "이메일 인증 | 3분 | Redis | 회원가입"이 이어짐). 원문 그대로 보존하였으며, account.md의 TokenService 명세(`generateReAuthToken`, TTL 기반 Redis 저장)를 참고하면 Re-auth Token도 Redis에 저장되는 것으로 추정된다.

### 6.11 페이징 및 정렬 표준

일관된 API 응답과 성능 최적화를 위해 페이징 및 정렬 기준을 표준화한다.

#### 6.11.1 페이징 방식

| 타입        | 사용 사례            | 특징                                |
| ----------- | -------------------- | ----------------------------------- |
| Page<T>     | 관리자, 통계, 게시판 | totalElements 포함, COUNT 쿼리 발생 |
| Slice<T>    | 모바일 무한 스크롤   | hasNext만 확인, COUNT 없음          |
| 커서 페이징 | 채팅 메시지          | sentAt 기준, 안정적 페이징          |

#### 6.11.2 기본 페이지 정책

● 모바일 앱: size = 20
● 관리자 화면: size = 50
● 채팅 메시지: size = 30 (커서 페이징)
● 최대 허용치: size = 100 (DoS 방지)

● 기본 정렬 기준

- 일반 목록: createdAt DESC
- 채팅: sentAt DESC
- 상점 검색: 사용자 선택

#### 6.11.3 정렬 필드 화이트리스트

허용되지 않은 정렬 필드를 차단하여 보안 및 성능 문제를 방지한다.

● 어노테이션

```java
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SortableFields {
    String[] value();
}
```

● 검증 로직

```java
public Sort validate(Sort sort, String[] allowedFields) {
    for (Sort.Order order : sort) {
        if (!allowed.contains(order.getProperty())) {
            throw new BadRequestException("허용되지 않은 정렬 필드");
        }
    }
    return sort;
}
```

● 사용 예시

```java
@GetMapping("/stores")
@SortableFields({"createdAt", "rating", "favoriteCount"})
public ResponseEntity<Page<StoreResponseDto>> getStoreList(...) { ... }
```

#### 6.11.4 커서 페이징

offset 기반 페이징의 성능 문제와 중복 노출 문제를 해결하기 위해 시간 기반 데이터는 커서 페이징을 사용한다.

● 적용 예시

```java
List<ChatMessage> messages = repository
    .findAllByChatRoomIdAndSentAtBefore(roomId, cursor, pageable);
```

● 설계 의도

- 대용량 데이터에서 성능 유지
- 데이터 삽입 시 중복 노출 방지
- 안정적인 페이징 보장

### 6.12 응답 표준화

모든 API 응답을 일관된 구조로 제공하여 클라이언트 개발 및 유지보수성을 향상시킨다.

#### 6.12.1 공통 응답 구조

● 성공 응답

```json
{
  "success": true,
  "data": {},
  "message": "요청이 성공했습니다",
  "timestamp": "...",
  "traceId": "..."
}
```

● 실패 응답

```json
{
  "success": false,
  "error": {
    "code": "ORDER_001",
    "message": "재고 부족"
  },
  "timestamp": "...",
  "traceId": "..."
}
```

#### 6.12.2 ApiResponse 클래스

```java
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorDetail error;
    private String message;
    private LocalDateTime timestamp;
    private String traceId;
}
```

● 설계 의도

- 모든 API 응답 형식 통일
- traceId 기반 로그 추적
- 클라이언트 파싱 단순화

#### 6.12.3 에러 코드 체계

| 접두사         | 도메인    | 예시           |
| -------------- | --------- | -------------- |
| AUTH_xxx       | 인증/인가 | AUTH_001       |
| ACCOUNT_xxx    | 회원      | ACCOUNT_001    |
| STORE_xxx      | 상점      | STORE_002      |
| ORDER_xxx      | 주문      | ORDER_001      |
| CHAT_xxx       | 채팅      | CHAT_001       |
| COMMUNITY_xxx  | 커뮤니티  | COMMUNITY_001  |
| COMMON_xxx     | 공통      | COMMON_404     |
| VALIDATION_xxx | 입력값    | VALIDATION_001 |

#### 6.12.4 GlobalExceptionHandler

● 핵심 처리

```
@ExceptionHandler(BusinessException.class)
→ 비즈니스 오류 처리

@ExceptionHandler(ForbiddenException.class)
→ 권한 오류 처리

@ExceptionHandler(MethodArgumentNotValidException.class)
→ 입력값 검증 오류

@ExceptionHandler(Exception.class)
→ 서버 오류
```

● 설계 의도

- 예외 처리 중앙 집중화
- 에러 응답 일관성 확보
- 로깅 용이

#### 6.12.5 HTTP 상태 코드 표준

| 상태 코드        | 사용 사례        |
| ---------------- | ---------------- |
| 200 OK           | 조회, 수정, 삭제 |
| 201 Created      | 리소스 생성      |
| 204 No Content   | 삭제 성공        |
| 400 Bad Request  | 입력 오류        |
| 401 Unauthorized | 인증 실패        |
| 403 Forbidden    | 권한 없음        |
| 404 Not Found    | 리소스 없음      |

### 6.13 로깅 전략

운영 안정성 확보 및 장애 대응을 위해 일관된 로깅 전략을 적용한다.
로그는 문제 분석, 사용자 행위 추적, 시스템 상태 모니터링을 위한 핵심 요소로 활용된다.

#### 6.13.1 로그 레벨 가이드

로그는 중요도에 따라 다음과 같이 구분하여 사용한다.

| 레벨  | 사용처                            | 예시                                 |
| ----- | --------------------------------- | ------------------------------------ |
| ERROR | 시스템 오류                       | DB 커넥션 실패, NullPointerException |
| WARN  | 비즈니스 규칙 위반, 외부 API 실패 | 잔액 부족, 결제 API 재시도           |
| INFO  | 주요 비즈니스 이벤트              | 주문 생성, 결제 완료, 회원가입       |
| DEBUG | 개발 환경 상세 흐름               | 메서드 진입/종료, 쿼리 파라미터      |
| TRACE | 최저 수준 상세                    | SQL 결과, HTTP 헤더 전체             |

● 운영 정책

- 운영 환경: INFO 이상 로그만 출력
- 개발 환경: DEBUG, TRACE 활용
- DEBUG/TRACE는 임시 디버깅 시에만 사용

#### 6.13.2 MDC (Mapped Diagnostic Context)

분산 환경에서 요청 단위 추적을 위해 MDC에 traceId를 저장한다.

● 적용 방식

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String traceId = request.getHeader("X-Trace-Id");
            if (traceId == null) {
                traceId = UUID.randomUUID().toString();
            }

            MDC.put("traceId", traceId);
            MDC.put("requestUri", request.getRequestURI());
            MDC.put("method", request.getMethod());

            chain.doFilter(request, response);

        } finally {
            MDC.clear(); // Thread Pool 재사용 시 메모리 누수 방지
        }
    }
}
```

● Logback 패턴

```
%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{traceId}] [%X{accountId}] %logger{36} - %msg%n
```

● 로그 출력 예시

```
2026-04-29 12:34:56.789 INFO [7f8e9d2a-...] [123] OrderService - 주문 생성 완료: orderId=456
```

● 설계 의도

- 요청 단위 로그 추적 (traceId)
- 장애 발생 시 로그 연관성 확보
- 멀티 스레드 환경에서 안전한 컨텍스트 관리

#### 6.13.3 비동기 로그 처리

로그 출력 성능 향상을 위해 비동기 로깅을 적용한다.

● Logback 설정

```xml
<configuration>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/eeum.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>logs/eeum-%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>10GB</totalSizeCap>
        </rollingPolicy>
    </appender>

    <appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="FILE"/>
        <queueSize>10000</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <neverBlock>true</neverBlock>
    </appender>

    <root level="INFO">
        <appender-ref ref="ASYNC_FILE"/>
    </root>

</configuration>
```

● 설계 의도

- 로그 I/O로 인한 성능 저하 방지
- 메인 스레드 블로킹 최소화
- 대량 로그 처리 안정성 확보

#### 6.13.4 민감 정보 마스킹

로그에 개인정보가 노출되지 않도록 마스킹 처리를 수행한다.

● 적용 예시

```java
@Component
public class SensitiveDataMaskingConverter extends ClassicConverter {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("([a-zA-Z0-9._-]{2})[a-zA-Z0-9._-]+(@[a-zA-Z0-9.-]+)");

    private static final Pattern PHONE_PATTERN =
        Pattern.compile("(\\d{3})-(\\d{4})-(\\d{4})");

    private static final Pattern PASSWORD_PATTERN =
        Pattern.compile("(\"password\"\\s*:\\s*)\"[^\"]+\"");

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();

        message = EMAIL_PATTERN.matcher(message).replaceAll("$1***$2");
        message = PHONE_PATTERN.matcher(message).replaceAll("$1-****-$3");
        message = PASSWORD_PATTERN.matcher(message).replaceAll("$1\"***\"");

        return message;
    }
}
```

● 설계 의도

- 개인정보 보호
- 로그 유출 시 보안 위험 최소화
- 규제 대응 (개인정보 보호 기준)

#### 6.13.5 비즈니스 이벤트 로깅

주요 비즈니스 이벤트는 INFO 레벨로 기록하여 서비스 상태 및 사용자 행동을 추적한다.

● 주요 로그 예시

| 이벤트      | 로그 메시지                                    |
| ----------- | ---------------------------------------------- |
| 회원가입    | 회원가입 완료: accountId=123, role=USER        |
| 로그인      | 로그인 성공: accountId=123, ip=192.168.0.1     |
| 주문 생성   | 주문 생성: orderId=456, totalPrice=25000       |
| 결제 완료   | 결제 완료: paymentId=789, amount=25000         |
| 사장 승인   | 사장 승인: ownerInfoId=42                      |
| 관리자 작업 | 관리자 작업: adminId=1, action=ACCOUNT_SUSPEND |

● 설계 의도

- 사용자 행동 추적
- 장애 분석 및 감사 로그 활용
- 운영 모니터링 기반 데이터 확보

## 7. API 설계

이음 시스템의 API는 RESTful 규칙을 따르며, Controller 계층을 기준으로 설계되었다.
각 도메인별 API는 다음과 같이 구성된다.

| 도메인           | 주요 기능                                      |
| ---------------- | ---------------------------------------------- |
| Auth             | 로그인, 회원가입, 토큰 재발급                  |
| Account          | 회원 정보 관리                                 |
| Store            | 상점 조회 및 관리                              |
| Order / Payment  | 주문 및 결제 처리                              |
| UsedProduct      | 중고거래                                       |
| Community        | 게시글 및 댓글                                 |
| Chat             | 채팅 (REST + WebSocket)                        |
| Review           | 상점 리뷰, 중고거래 리뷰, 답글                 |
| Notification     | 알림 조회, 읽음 처리, 알림 설정, FCM 토큰 관리 |
| Inquiry / Report | 문의 작성 및 답변, 신고 접수 및 처리           |
| Admin            | 관리자 기능                                    |

상세 API는 Controller 명세 기준으로 설계되었으며, 본 목차에서는 중복을 방지하기 위해 별도 기술하지 않는다.
(각 도메인 Controller 상세 명세는 account.md, store.md, order-payment.md, reservation.md, used-product.md, community.md, chat.md, notification.md, favorite.md, inquiry.md, sdd-report.md, category.md, image.md 참조)

## 8. 설계 시퀀스 다이어그램 (전체 목록)

| 식별 번호 | 기능                          | 분리 위치        |
| --------- | ----------------------------- | ---------------- |
| DSEQ-1    | 회원가입 (이메일 인증)        | account.md       |
| DSEQ-2    | 로그인 (JWT 발급)             | account.md       |
| DSEQ-3    | 토큰 재발급                   | account.md       |
| DSEQ-4    | 비밀번호 재설정               | account.md       |
| DSEQ-5    | 활동지역 등록 (위치 인증)     | account.md       |
| DSEQ-6    | 회원 탈퇴 (Soft Delete)       | account.md       |
| DSEQ-7    | 사장 매장 등록                | store.md         |
| DSEQ-8    | 매장 상세 조회 (사용자)       | store.md         |
| DSEQ-9    | 상품 등록 (옵션 포함)         | store.md         |
| DSEQ-10   | 이벤트 상품 등록              | store.md         |
| DSEQ-11   | 매장 리뷰 작성 + 답글         | store.md         |
| DSEQ-12   | 장바구니 담기 (옵션)          | order-payment.md |
| DSEQ-13   | 주문 생성 + 결제 (PortOne)    | order-payment.md |
| DSEQ-14   | PortOne Webhook 처리          | order-payment.md |
| DSEQ-15   | 주문 취소 + 환불              | order-payment.md |
| DSEQ-16   | 매장 방문 예약 (capacity)     | reservation.md   |
| DSEQ-17   | 중고상품 등록                 | used-product.md  |
| DSEQ-18   | 거래 완료 처리                | used-product.md  |
| DSEQ-19   | 게시글 작성                   | community.md     |
| DSEQ-20   | 댓글 + 좋아요                 | community.md     |
| DSEQ-21   | 1:1 채팅방 생성 (멱등)        | chat.md          |
| DSEQ-22   | 메시지 발송 (WebSocket)       | chat.md          |
| DSEQ-23   | 메시지 읽음 처리              | chat.md          |
| DSEQ-24   | 이미지 업로드 (Presigned URL) | image.md         |
| DSEQ-25   | 찜 토글                       | favorite.md      |
| DSEQ-26   | 알림 발송 (이벤트 기반)       | notification.md  |
| DSEQ-27   | 카테고리 트리 조회 (캐싱)     | category.md      |
| DSEQ-28   | 문의 등록 + 답변              | inquiry.md       |
| DSEQ-29   | 신고 등록                     | sdd-report.md    |
| DSEQ-30   | 사장 승인 (감사 로그)         | account.md       |

[다이어그램 원본은 기존 SDD 문서 참조]

## 9. 데이터베이스 설계

### 9.1 Core ERD

[다이어그램 원본은 기존 SDD 문서 참조]

### 9.2 도메인 ERD (목록)

- Account / Region 도메인 → account.md
- Store / Product 도메인 → store.md
- Order / Payment 도메인 → order-payment.md, reservation.md
- UsedProduct 도메인 → used-product.md
- Community 도메인 → community.md
- Chat 도메인 → chat.md
- Admin / Notification 도메인 → account.md, notification.md

[다이어그램 원본은 기존 SDD 문서 참조]

### 9.3 테이블 정의 (전체 목록 및 분리 위치)

원문 9.3절의 전체 테이블 정의는 다음과 같이 도메인별 파일로 분리되었다.

| 테이블명                                                                                                                                                                | 분리 위치                          |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------- |
| Account, OwnerInfo, AccountRegion, Region, Location, AdminAuditLog                                                                                                      | account.md                         |
| Store, StoreNotice, StoreImage, ProductCategory, Product, ProductImage, ProductOption, ProductOptionItem, EventProduct, StoreReview, StoreReviewImage, StoreReviewReply | store.md                           |
| Order, OrderItem, Payment, Cart, CartItem                                                                                                                               | order-payment.md                   |
| Reservation                                                                                                                                                             | reservation.md                     |
| UsedProduct, UsedProductImage, UsedProductReview                                                                                                                        | used-product.md                    |
| CommunityPost, CommunityImage, CommunityComment, CommunityCommentReply, PostLike                                                                                        | community.md                       |
| ChatRoom, ChatParticipant, ChatMessage                                                                                                                                  | chat.md                            |
| Category                                                                                                                                                                | category.md                        |
| Favorite                                                                                                                                                                | favorite.md                        |
| Notification, NotificationSettings                                                                                                                                      | notification.md                    |
| Inquiry, InquiryReply, InquiryImage                                                                                                                                     | inquiry.md                         |
| (Report 테이블 정의 — 원문에 누락)                                                                                                                                      | sdd-report.md (참고용 메모만 존재) |

> 원문 9.3절은 Account부터 InquiryImage까지 순서대로 테이블을 정의한 뒤 곧바로 10장(UI 설계)으로 이어진다. Report 테이블 정의는 원문에 존재하지 않는다 (sdd-report.md 참조).

## 10. UI 설계

UI 설계는 일반 사용자용 모바일 앱(React Native)과 사장 회원·관리자용 웹(React)으로 구분하여 작성되었으며, 본 SDD의 API 설계 및 유스케이스 명세와 연계하여 활용한다.

본 시스템의 UI 설계는 Figma를 활용하여 별도 작성되었으며, 아래 링크를 통해 확인할 수 있다.

● Figma 디자인 링크
https://www.figma.com/design/OCb1Og1xHyja7HIantFlr6/EEUM?node-id=0-1&t=xsgW4Zm1hyCB0p3C-1
● Figma 사용자 UI
https://www.figma.com/make/ET9I5hbN2eYBNA3IiIS2KL/%EC%82%AC%EC%9A%A9%EC%9E%90-UI?t=EitkOeDdBmtvP0rj-1&preview-route=%2Fhome
● Figma 관리자 UI
https://www.figma.com/make/1ji7jbYheAtnSLr1i9IndM/%EA%B4%80%EB%A6%AC%EC%9E%90-%EB%8C%80%EC%8B%9C%EB%B3%B4%EB%93%9C?t=dB0mEG76sWX7Wlw0-6
● Figma 사장회원 UI
https://www.figma.com/make/xxaIJItn6Fn5MRAxxZYBXn/%EC%82%AC%EC%9E%A5%EB%8B%98%EC%9A%A9-%EB%8C%80%EC%8B%9C%EB%B3%B4%EB%93%9C?t=dB0mEG76sWX7Wlw0-6

## 11. 설계 컴포넌트 다이어그램

이음 시스템의 논리적 컴포넌트와 의존 관계를 표현한다. Spring Boot 기반 단일 애플리케이션이지만 계층 + 도메인으로 구성된 구조이다.

### 11.1 컴포넌트 구성도

[다이어그램 원본은 기존 SDD 문서 참조]

### 11.2 컴포넌트 의존 관계

| 컴포넌트              | 역할                                               | 주요 의존 대상                  |
| --------------------- | -------------------------------------------------- | ------------------------------- |
| Presentation Layer    | 클라이언트 요청 수신 및 응답 반환 (REST,WebSocket) | Service Layer                   |
| Cross-Cutting (AOP)   | 인증, 감사, 분산 락, 멱등성 처리 등 공통 기능 수행 | Redis, AdminAuditService        |
| Service Layer         | 비즈니스 로직 수행 및 트랜잭션 경계 설정           | Repository Layer, External APIs |
| Repository Layer      | 데이터베이스 접근 및 쿼리 수행                     | MySQL                           |
| MySQL                 | 서비스 데이터 영속 저장                            | -                               |
| Redis                 | 캐시, 분산 락, 토큰 저장, 읽지 않은 알림 수 관리   | -                               |
| PortOne               | 외부 결제 시스템 연동 및 Webhook 처리              | -                               |
| FCM                   | 사용자 푸시 알림 전송                              | -                               |
| AWS S3                | 이미지 파일 저장 및 URL 제공                       | -                               |
| OAuth (Kakao / Naver) | 소셜 로그인 인증 및 사용자 정보 제공               | -                               |

## 12. 설계 디플로먼트 다이어그램

이음 시스템의 물리적 배포 구조를 표현한다. AWS 프리티어 기반의 단일 인스턴스 구성이다.

### 12.1 배포 구성도

[다이어그램 원본은 기존 SDD 문서 참조]

### 12.2 배포 노드 명세

이음 시스템은 AWS 프리티어 환경을 기반으로 단일 인스턴스 구조로 구성되며, 핵심 컴포넌트를 EC2 인스턴스에 통합하여 운영한다.

| 노드                | 종류              | 역할                                  | 사양                       |
| ------------------- | ----------------- | ------------------------------------- | -------------------------- |
| Route 53            | DNS               | 도메인 → EC2 라우팅                   | (도메인 비용 발생)         |
| EC2                 | 애플리케이션 서버 | Nginx + Spring Boot + Redis 통합 운영 | t2.micro (1 vCPU, 1GB RAM) |
| Nginx               | 리버스 프록시     | HTTPS 종료, 정적 파일 서빙            | EC2 내부                   |
| Spring Boot         | 애플리케이션      | 비즈니스 로직 실행                    | Java 17, Embedded Tomcat   |
| Redis               | 인메모리 저장소   | 캐시 / 분산 락 / 토큰 저장            | Docker 컨테이너            |
| RDS MySQL           | RDB               | 영속 데이터 저장                      | db.t3.micro (20GB)         |
| AWS S3              | 객체 스토리지     | 이미지 저장                           | 5GB 무료                   |
| PortOne             | 외부 결제         | 결제 처리 + Webhook                   | 외부 서비스                |
| FCM                 | 푸시 서비스       | 모바일/웹 알림 발송                   | 무료                       |
| OAuth (Kakao/Naver) | 인증              | 소셜 로그인                           | 무료                       |
| KakaoMap API        | 지도              | 지도 표시 및 좌표 변환                | 무료 한도                  |

● 설계 특징

- 단일 EC2 인스턴스 기반 경량 아키텍처
- 외부 서비스 적극 활용 (결제, 지도, 인증)
- 이미지 및 정적 리소스는 S3로 분리

### 12.3 통신 프로토콜

시스템 구성 요소 간 통신은 다음과 같이 정의한다.

| 구간                   | 프로토콜    | 포트 | 비고                      |
| ---------------------- | ----------- | ---- | ------------------------- |
| 사용자 ↔ Nginx         | HTTPS (TLS) | 443  | Let's Encrypt 인증서 적용 |
| Nginx ↔ Spring Boot    | HTTP        | 8080 | EC2 내부 통신             |
| Spring Boot ↔ Redis    | TCP         | 6379 | localhost                 |
| Spring Boot ↔ RDS      | TCP         | 3306 | VPC 내부                  |
| Spring Boot ↔ S3       | HTTPS       | 443  | IAM Role 기반             |
| Spring Boot ↔ 외부 API | HTTPS       | 443  | API 호출 및 Webhook 수신  |
| 채팅 (WebSocket)       | WSS         | 443  | STOMP over WebSocket      |

● 설계 특징

- 외부 통신은 모두 HTTPS 기반
- 내부 통신은 성능을 고려하여 HTTP/TCP 사용
- WebSocket을 통한 실시간 채팅 지원

### 12.4 프리티어 운영 전략

AWS 프리티어 환경 제약을 고려하여 비용 효율적인 구조로 설계하였다.

● 운영 전략

- 단일 EC2 통합 운영
  - Nginx + Spring Boot + Redis를 하나의 인스턴스에서 실행
- Redis Docker 사용
  - ElastiCache 미사용 → Docker 컨테이너로 직접 운영
- RDS 분리 운영
  - 데이터 안정성을 위해 DB는 별도 관리
- S3 활용
  - 이미지 저장을 S3로 분리 (Presigned URL 방식)
  - EC2 디스크 부담 최소화
- CloudFront 미사용
  - 초기 트래픽이 적어 CDN 불필요

● 설계 의도

- 비용 최소화
- 단순 구조 유지
- 빠른 개발 및 배포 가능

### 12.5 한계 및 향후 확장

현재 프리티어 환경은 비용 효율적이지만, 트래픽 증가 시 확장이 필요하다.

● 확장 전략

| 항목          | 현재(프리티어) | 확장 시                       |
| ------------- | -------------- | ----------------------------- |
| EC2           | t2.micro 1대   | t3.medium 이상 + Auto Scaling |
| Redis         | Docker (단일)  | ElastiCache (Multi-AZ)        |
| RDS           | 단일 인스턴스  | Master + Read Replica         |
| Load Balancer | Nginx 단독     | ALB 도입                      |
| 이미지 배포   | S3 직접        | CloudFront CDN 적용           |

● 확장 방향

- 수평 확장 (Scale-Out)
- 고가용성 구조 (Multi-AZ)
- 캐시 및 DB 분산 처리
- CDN을 통한 정적 리소스 최적화

## 13. 요구사항 추적표

> 원문에는 "13. 요구사항 추적표" 제목만 존재하며 표 내용은 기재되어 있지 않다 (문서 마지막 줄). 원문 그대로 보존함.
