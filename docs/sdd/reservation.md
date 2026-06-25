# Reservation 도메인 SDD

> 코드 경로: reservation, visitReservation, reservationSetting, timeSlot
> 비고: SDD 원문에서는 Order/Payment 안에 있으나 실제 구현 도메인 기준으로 분리

## 4. 설계 클래스 다이어그램 - 관련 기능 설명 (DCOM-3 일부)

본 도메인은 SDD 원문 DCOM-3 "동네 상점 예약 및 결제" 설명의 일부로 기술되어 있다 (전문은 `order-payment.md` 참조).

설명 (원문 발췌)
또한 예약 기능은 일반 주문과 구분되는 속성을 가지므로 Reservation 엔티티로 별도로 분리하여 관리하였다. 예약은 특정 주문과 연관되며, 예약 시간 및 예약 상태 정보를 포함한다. 이를 통해 일반 상품 결제와 방문 예약을 하나의 주문 흐름 안에서 유연하게 처리할 수 있도록 설계하였다.

## 5.3 Order/Payment 도메인 내 Reservation 명세 (원문 위치)

### ● Entity

«Entity» Reservation
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
reservationId | Long | PK, NOT NULL | 고유 식별자
accountId | Long | FK, NOT NULL | 예약자
storeId | Long | FK, NOT NULL | 예약 상점
productId | Long | FK, nullable | 예약 상품(상품 예약 시)
orderId | Long | FK, nullable | 연관 주문
reservedAt | LocalDateTime | NOT NULL | 예약 일시
status | ReservationStatus | NOT NULL | PENDING / CONFIRMED / EXPIRED / CANCELLED
reservationType | ReservationType | NOT NULL | VISIT / PRODUCT
totalPrice | BigDecimal | nullable | PRODUCT 타입일 때만 값 존재, VISIT 타입은 결제 없으므로 null
capacity | int | nullable | 예약 최대 가능 인원 (VISIT 타입에서만 사용)
version | Long | NOT NULL | 낙관적 락
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

> 참고: 원문에는 Reservation에 대한 별도 도메인 메서드 명세가 없음 (Order/Payment 도메인 메서드 절에 포함되지 않음). 상태 전이는 Service 계층(ReservationService, OwnerReservationService)과 Helper(ReservationAccessHelper)에서 처리되는 것으로 기술되어 있다.

### ● Controller

«Controller» ReservationController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
POST | /reservations/product | ResponseEntity<ReservationResponseDto> | 상품 예약 생성
POST | /reservations/store | ResponseEntity<ReservationResponseDto> | 상점 방문 예약 생성
GET | /reservations/me | ResponseEntity<Page<ReservationResponseDto>> | 내 예약 목록 (필터: 기간, 상태, 타입)
GET | /reservations/me/{reservationId} | ResponseEntity<ReservationResponseDto> | 내 예약 상세
PATCH | /reservations/me/{reservationId}/cancel | ResponseEntity<CommonResponseDto> | 예약 취소

«Controller» OwnerReservationController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /owner/stores/me/{storeId}/reservations | ResponseEntity<Page<StoreReservationResponseDto>> | 사장용 예약 목록
GET | /owner/stores/me/{storeId}/reservations/{reservationId} | ResponseEntity<StoreReservationDetailResponseDto> | 사장용 예약 상세
PATCH | /owner/stores/me/{storeId}/reservations/{reservationId}/confirm | ResponseEntity<CommonResponseDto> | 예약 확정
PATCH | /owner/stores/{storeId}/reservations/{reservationId}/reject | ResponseEntity<CommonResponseDto> | 예약 거절

### ● Service

«Service» ReservationService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
createProductReservation(accountId, ReservationCreateRequestDto) | ReservationResponseDto | REQUIRED | StockCheck + Event | 상품 예약 생성 (재고 확인 → 예약 생성 → status=PENDING)
createStoreReservation(accountId, ReservationCreateRequestDto) | ReservationResponseDto | REQUIRED | RedisLock("reservation:{storeId}:{reservedAt:yyyy-MM-dd-HH-mm}") + CapacityCheck + Event | 매장 방문 예약 생성 (시간 중복 확인 + capacity 검증 → 예약 생성)
getMyReservations(accountId, ReservationSearchDto, Pageable) | Page<ReservationResponseDto> | readOnly | - | 내 예약 목록 (필터: 기간, 상태, 타입)
getMyReservationDetail(accountId, reservationId) | ReservationResponseDto | readOnly | Ownership | 내 예약 상세
cancelReservation(accountId, reservationId, reason) | void | REQUIRED | Ownership + Cancellable + Event | 예약 취소 (status=CANCELLED → 필요 시 재고 복구)

«Service» OwnerReservationService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getStoreReservations(accountId, storeId, ReservationSearchDto, Pageable) | Page<StoreReservationResponseDto> | readOnly | Ownership | 사장용 예약 목록 (필터: 기간, 상태, 타입)
getStoreReservationDetail(accountId, storeId, reservationId) | StoreReservationDetailResponseDto | readOnly | Ownership | 사장용 예약 상세
confirmReservation(accountId, storeId, reservationId) | void | REQUIRED | Ownership + Event | 예약 확정 (사장) → status=CONFIRMED → 사용자 알림

«Helper» ReservationAccessHelper
메서드명 | 반환타입 | 설명
---|---|---
verifyReservationOwnership(accountId, reservationId) | Reservation | 예약 소유권 검증 (예약자)
verifyStoreReservationAccess(accountId, storeId, reservationId) | Reservation | 사장용: 본인 상점 + 해당 예약 검증
verifyCancellable(reservationId) | Reservation | 취소 가능 상태 검증 (PENDING/CONFIRMED)
verifyConfirmable(reservationId) | Reservation | 확정 가능 상태 검증 (PENDING만 허용)
verifyNotExpired(reservationId) | Reservation | 예약 만료 여부 검증 (reservedAt 기준)

### ● Repository

«Repository» ReservationRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long reservationId) | Optional<Reservation> | 예약 ID로 조회
findByIdAndAccountId(Long reservationId, Long accountId) | Optional<Reservation> | 본인 예약 검증용
findByIdForUpdate(Long reservationId) | Optional<Reservation> | @Lock(PESSIMISTIC_WRITE) / 예약 취소·확정 시 동시성 제어
findAllByAccountId(Long accountId, Pageable pageable) | Page<Reservation> | 사용자 예약 목록
findAllByAccountIdAndStatus(Long accountId, ReservationStatus status, Pageable pageable) | Page<Reservation> | 상태별 사용자 예약 목록
findAllByAccountIdAndReservationType(Long accountId, ReservationType type, Pageable pageable) | Page<Reservation> | 타입별 사용자 예약 (VISIT/PRODUCT)
findAllByStoreId(Long storeId, Pageable pageable) | Page<Reservation> | 사장용 예약 목록
findAllByStoreIdAndStatus(Long storeId, ReservationStatus status, Pageable pageable) | Page<Reservation> | 사장용 상태별 조회
findAllByStoreIdAndReservedAtBetween(Long storeId, LocalDateTime start, LocalDateTime end) | List<Reservation> | 매장 예약 일정 조회 (캘린더용)
countByStoreIdAndReservedAtBetweenAndStatus(Long storeId, LocalDateTime startOfMinute, LocalDateTime endOfMinute, ReservationStatus status) | Long | 분 단위 동시 예약 수 (capacity 검증용, Redis Lock과 함께 사용)
existsByOrderId(Long orderId) | boolean | 주문 연결 예약 중복 방지
findByOrderId(Long orderId) | Optional<Reservation> | 주문 연결 예약 조회

## 5.8 ENUM (Reservation 관련)

«Enum» ReservationStatus
ENUM 값 | 설명
---|---
PENDING | 예약 대기
CONFIRMED | 예약 확정
EXPIRED | 예약 만료
CANCELLED | 예약 취소

«Enum» ReservationType
ENUM 값 | 설명
---|---
VISIT | 매장 방문 예약
PRODUCT | 상품 예약

«Enum» NotificationType (예약 관련, 중복 참조 — notification.md 참조)
ENUM 값 | 설명
---|---
RESERVATION_REQUESTED | 예약 요청
RESERVATION_CONFIRMED | 예약 확정
RESERVATION_REJECTED | 예약 거절
RESERVATION_CANCELLED | 예약 취소
RESERVATION_EXPIRED | 예약 만료

## 8. 설계 시퀀스 다이어그램 (관련)

- DSEQ-16: 매장 방문 예약 (capacity)

[다이어그램 원본은 기존 SDD 문서 참조]

## 9.2 도메인 ERD (참조)

Reservation 테이블은 원문 9.2절의 "Order / Payment 도메인" ERD 항목 내에 포함되어 있다.
[다이어그램 원본은 기존 SDD 문서 참조]

## 9.3 테이블 정의

### Reservation (예약)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
reservation_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 예약 고유 식별자
account_id | bigint | FK(account), NOT NULL | 예약자
store_id | bigint | FK(store), NOT NULL | 예약 상점
product_id | bigint | FK(product), nullable | 예약 상품(상품 예약 시)
order_id | bigint | FK(order), nullable | 연관 주문
reserved_at | datetime | NOT NULL | 예약 일시
status | varchar(20) | NOT NULL | ReservationStatus ENUM
reservation_type | varchar(20) | NOT NULL | ReservationType ENUM
total_price | decimal(10,2) | nullable | PRODUCT 타입일 때만 값 존재
capacity | int | nullable | 예약 최대 가능 인원(VISIT 타입에서만)
version | bigint | NOT NULL | 낙관적 락(@Version)
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

> 참고: SDD 원문 9.3절 테이블 정의에는 `StoreVisitReservationSetting`, `VisitReservationTimeSlot` 테이블 정의가 별도로 존재하지 않는다 (CLAUDE.md에 언급된 `VisitReservationTimeSlot`, `StoreVisitReservationSetting` 엔티티는 코드베이스 구현체이며, capacity 기반 단순 동시성 제어로 SDD에는 분 단위 카운트 쿼리(`countByStoreIdAndReservedAtBetweenAndStatus`)로 대체 기술되어 있음). 시간 슬롯/예약 설정 세부 설계는 코드베이스 구현을 기준으로 별도 보강이 필요함.

## 중복 참조 (common.md / order-payment.md에도 기재됨)

- Redis 분산 락 키: `reservation:{storeId}:{yyyy-MM-dd-HH-mm}` (TTL 3s, 매장 방문 예약)은 `common.md` 6.1.2 참조
- 비관적 락: `ReservationRepository.findByIdForUpdate`(예약 확정 및 취소)는 `common.md` 6.1.3 참조
- 낙관적 락 적용 대상 Entity(Reservation)는 `common.md` 6.1.4 참조
- 이벤트 카탈로그: ReservationConfirmedEvent 등은 `common.md` 6.2.5, 6.2.2(이벤트 명명 규칙) 참조
- DSEQ-16(매장 방문 예약, capacity) 시퀀스는 `common.md` 8장 목록 참조
