# Order/Payment 도메인 SDD

> 코드 경로: order, payment, cart, orderItem
> 비고: 주문, 결제, 장바구니

## 4. 설계 클래스 다이어그램 - 관련 기능 설명

### DCOM-3. 동네 상점 예약 및 결제

설계다이어그램
[다이어그램 원본은 기존 SDD 문서 참조]

설명
본 기능은 사용자의 상품 주문, 장바구니 관리, 결제 처리 및 예약 기능을 포함하는 기능으로, 상품 선택부터 결제 완료까지의 전반적인 흐름을 관리한다.

사용자는 상품을 장바구니에 담을 수 있으며, 장바구니는 Cart 엔티티를 통해 사용자별로 관리된다. 장바구니는 하나의 가게 단위로 유지되도록 설계하였으며, 다른 가게의 상품을 담을 경우 기존 장바구니 항목을 초기화하고 새로운 가게 기준으로 다시 구성되도록 하였다. 장바구니에 담긴 개별 상품 정보는 CartItem 엔티티를 통해 관리된다.

사용자가 결제를 진행하면 장바구니의 상품 정보는 Order와 OrderItem 엔티티로 변환되어 주문 정보로 저장된다. Order는 주문의 전체 정보를 관리하며, OrderItem은 주문에 포함된 개별 상품의 수량 및 가격 정보를 관리한다.

결제 처리는 Payment 엔티티를 통해 이루어지며, 외부 결제 시스템과 연동하여 결제 상태를 관리한다. 결제 성공 여부에 따라 주문 상태가 갱신되며, 이를 통해 주문의 진행 상태를 일관되게 관리할 수 있도록 설계하였다.

또한 예약 기능은 일반 주문과 구분되는 속성을 가지므로 Reservation 엔티티로 별도로 분리하여 관리하였다. 예약은 특정 주문과 연관되며, 예약 시간 및 예약 상태 정보를 포함한다. 이를 통해 일반 상품 결제와 방문 예약을 하나의 주문 흐름 안에서 유연하게 처리할 수 있도록 설계하였다.

> 참고: SDD 원문에서는 Reservation이 본 DCOM-3 설명 및 5.3절(Order/Payment 도메인)에 함께 기술되어 있으나, 실제 구현 도메인 기준으로 `reservation.md`에 별도 분리하였다. Reservation 관련 Entity/Controller/Service/Repository는 `reservation.md` 참조.

## 5.3 Order/Payment 도메인

### ● Entity

«Entity» Order
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
orderId | Long | PK, NOT NULL | 고유 식별자
accountId | Long | FK, NOT NULL | 구매자
storeId | Long | FK, NOT NULL | 주문 상점
totalPrice | BigDecimal | NOT NULL | 총 결제 금액
orderNumber | String | UNIQUE, NOT NULL | 사용자 표시용 주문번호
version | Long | NOT NULL | 낙관적 락용 (@Version)
status | OrderStatus | NOT NULL | PENDING / PAID / FAILED / CANCELLED / REFUNDED
paidAt | LocalDateTime | nullable | 결제 완료 시점
cancelledAt | LocalDateTime | nullable | 취소 시점
cancelReason | String | nullable | 취소 사유
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» OrderItem
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
orderitemId | Long | PK, NOT NULL | 고유 식별자
orderId | Long | FK, NOT NULL | 연관 주문
productId | Long | FK, nullable | 연관 상품
eventproductId | Long | FK, nullable | 연관 이벤트상품
productname | String | NOT NULL | 주문 시점 상품명
selectedOptions | String | NOT NULL | 주문 시 선택된 옵션 스냅샷(JSON)
quantity | int | NOT NULL | 수량
price | BigDecimal | NOT NULL | 주문 시점 가격 고정

«Entity» Payment
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
paymentId | Long | PK, NOT NULL | 고유 식별자
orderId | Long | FK, NOT NULL | 연관 주문(1:1)
accountId | Long | FK, NOT NULL | 결제자
portOnePaymentId | String | UNIQUE, NOT NULL | PortOne 결제 고유 번호
idempotencyKey | String | UNIQUE, NOT NULL | 중복 결제 방지 (멱등성 키)
amount | BigDecimal | NOT NULL | 결제 금액
status | PaymentStatus | NOT NULL | PENDING / PAID / FAILED / CANCELLED / REFUNDED
pgProvider | String | nullable | PG사 이름
paymentMethod | PaymentMethod (ENUM) | NOT NULL | CARD / KAKAOPAY / NAVERPAY / TOSS 등
paidAt | LocalDateTime | nullable | 결제 완료 시점
cancelledAt | LocalDateTime | nullable | 취소 시점
failReason | String | nullable | 결제 실패 사유 (PortOne 응답)
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» Cart
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
cartId | Long | PK, NOT NULL | 고유 식별자
accountId | Long | FK, NOT NULL, UNIQUE | 회원
storeId | Long | FK, NOT NULL, UNIQUE | 동일 상점 제약 관리
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

> UNIQUE 제약: (cartId, productId, eventproductId, selectedOptionsHash)
> CHECK 제약: productId IS NOT NULL XOR eventproductId IS NOT NULL

«Entity» CartItem
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
cartitemId | Long | PK, NOT NULL | 고유 식별자
cartId | Long | FK, NOT NULL | 연관 장바구니
productId | Long | FK, nullable | 연관 상품 UNIQUE 제약: (cartId, productId), (cartId, eventproductId)
eventproductId | Long | FK, nullable | 연관 이벤트 상품 UNIQUE 제약: (cartId, productId), (cartId, eventproductId)
selectedOptionItemIds | String | nullable | 선택한 옵션 ID 목록(JSON)
optionsTotalPrice | BigDecimal | NOT NULL | 옵션 추가 가격 총합
selectedOptionsHash | String | NOT NULL | 옵션 조합 구분용 해시값
quantity | int | NOT NULL, ≥1 | 수량
price | BigDecimal | NOT NULL | 추가할 당시 가격 고정

«Entity» Order (도메인 메서드)
메서드명 | 타입 | 제약조건
---|---|---
markPaid() | void | status = PAID
cancel() | void | status = CANCELLED
refund() | void | status = REFUNDED
isCancelable() | boolean | 취소 가능 상태 반환

«Entity» Payment (도메인 메서드)
메서드명 | 타입 | 제약조건
---|---|---
markPaid() | void | status = PAID
markFailed() | void | status = FAILED
cancel() | void | status = CANCELLED
refund() | void | status = REFUNDED
isAlreadyProcessed() | boolean | 이미 처리된 웹훅 여부 반환

### ● Controller

«Controller» OrderController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
POST | /orders | ResponseEntity<OrderResponseDto> | 주문 생성 (장바구니 → 주문)
GET | /orders/me | ResponseEntity<Page<OrderResponseDto>> | 내 주문 목록 (필터: 기간, 상태)
GET | /orders/me/{orderId} | ResponseEntity<OrderDetailResponseDto> | 내 주문 상세
PATCH | /orders/me/{orderId}/cancel | ResponseEntity<CommonResponseDto> | 주문 취소 (PENDING 상태만)
POST | /orders/me/{orderId}/refund | ResponseEntity<CommonResponseDto> | 환불 요청 (PAID 상태만)

«Controller» PaymentController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
POST | /payments | ResponseEntity<PaymentResponseDto> | 결제 요청
POST | /payments/webhook | ResponseEntity<CommonResponseDto> | PortOne 웹훅 수신(PortOne webhook 서명 검증, 위변조 요청 차단)
GET | /payments/me | ResponseEntity<Page<PaymentResponseDto>> | 내 결제 내역
GET | /payments/me/{paymentId} | ResponseEntity<PaymentResponseDto> | 결제 상세 조회
PATCH | /payments/{paymentId}/cancel | ResponseEntity<CommonResponseDto> | 결제 취소
POST | /payments/{paymentId}/refund | ResponseEntity<CommonResponseDto> | 환불 요청

«Controller» CartController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /cart | ResponseEntity<CartResponseDto> | 장바구니 조회 (목록 + 총 금액)
GET | /cart/count | ResponseEntity<CartCountResponseDto> | 장바구니 상품 수
POST | /cart/items | ResponseEntity<CartItemResponseDto> | 장바구니 상품 추가
PATCH | /cart/items/{cartItemId} | ResponseEntity<CartItemResponseDto> | 장바구니 수량 변경
DELETE | /cart/items/{cartItemId} | ResponseEntity<CommonResponseDto> | 장바구니 상품 삭제
DELETE | /cart | ResponseEntity<CommonResponseDto> | 장바구니 전체 비우기

«Controller» OwnerOrderController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /stores/me/{storeId}/orders | ResponseEntity<Page<StoreOrderResponseDto>> | 사장용 주문 목록 (필터: 기간, 상태)
GET | /stores/me/{storeId}/orders/{orderId} | ResponseEntity<StoreOrderDetailResponseDto> | 사장용 주문 상세
PATCH | /stores/me/{storeId}/orders/{orderId}/confirm | ResponseEntity<CommonResponseDto> | 주문 확정
PATCH | /stores/me/{storeId}/orders/{orderId}/refund/approve | ResponseEntity<CommonResponseDto> | 환불 요청 승인
PATCH | /stores/me/{storeId}/orders/{orderId}/refund/reject | ResponseEntity<CommonResponseDto> | 환불 요청 거절

> 참고: `/owner/stores/{storeId}/orders`, `/owner/stores/{storeId}/orders/{orderId}` (StoreDashboard 연계) API는 `store.md`의 OwnerStoreController에도 동일/유사 항목이 기재되어 있음 (원문 중복).

### ● Service

«Service» OrderService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
createOrder(accountId, OrderCreateRequestDto) | OrderResponseDto | REQUIRED | RedisLock(productIds) + StockCheck + Event | 장바구니 → 주문 생성 (재고 차감 + Order/OrderItem 저장 + orderNumber 발급)
getMyOrders(accountId, OrderSearchDto, Pageable) | Page<MyOrderResponseDto> | readOnly | - | 내 동네상점 주문/예약 내역 (필터: 기간, 상태)
getMyOrderDetail(accountId, orderId) | MyOrderDetailResponseDto | readOnly | Ownership | 내동네 상점 주문 상세 (OrderItem + Payment 정보 포함)
cancelOrder(accountId, orderId, reason) | void | REQUIRED | Ownership + Cancellable + Event | 주문 취소 (PENDING/PAID 상태) → 재고 복구 + 결제 취소 이벤트 발행
refundOrder(accountId, orderId, reason) | void | REQUIRED | Ownership + Refundable + Event | 환불 요청 → 환불 이벤트 발행 (PaymentService가 처리)
markAsPaid(orderId) | void | REQUIRED | Internal + OptimisticLock | Payment Webhook에서 호출 → status=PAID + paidAt 기록

«Service» PaymentService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
requestPayment(accountId, PaymentRequestDto) | PaymentResponseDto | REQUIRED | Idempotent + RedisLock(orderId) | 멱등성 키 검증 → Redis 락 → 재고 확인 → PortOne 결제 요청 → Payment 생성
handleWebhook(WebhookRequestDto) | void | REQUIRED | Idempotent + Event | 중복 웹훅 확인 (portOnePaymentId) → 금액 검증 → Order 상태 변경 → 재고 차감 확정
getMyPayments(accountId, Pageable) | Page<PaymentResponseDto> | readOnly | - | 내 결제 내역
getMyPaymentDetail(accountId, paymentId) | PaymentResponseDto | readOnly | Ownership | 내 결제 상세
cancelPayment(accountId, paymentId) | void | REQUIRED | Ownership + RedisLock + Event | Redis 락 → PortOne 취소 호출 → status=CANCELLED → Order 상태 동기화
refundPayment(accountId, paymentId, reason) | void | REQUIRED | Ownership + Event | PortOne 환불 호출 → status=REFUNDED → 재고 복구
verifyPayment(paymentId, amount) | boolean | readOnly | - | PortOne 결제 검증 (금액 일치 확인)

«Service» CartService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getCart(accountId) | CartResponseDto | readOnly | - | 장바구니 목록 + 총 금액
getCartItemCount(accountId) | CartCountResponseDto | readOnly | - | 장바구니 상품 수
addToCart(accountId, CartAddRequestDto) | CartItemResponseDto | REQUIRED | SameStoreCheck + StockCheck | 동일 상점 검증 → 다르면 기존 초기화 → 재고 확인 → CartItem 저장
updateCartItemQuantity(accountId, cartItemId, CartItemUpdateRequestDto) | CartItemResponseDto | REQUIRED | Ownership + StockCheck | 본인 카트 검증 + 재고 확인 + 수량 갱신
deleteCartItem(accountId, cartItemId) | void | REQUIRED | Ownership | 장바구니 항목 삭제
clearCart(accountId) | void | REQUIRED | - | 장바구니 전체 비우기

«Service» OwnerOrderService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getStoreOrders(accountId, storeId, OrderSearchDto, Pageable) | Page<StoreOrderResponseDto> | REQUIRED | Ownership | 사장용 주문 목록 (필터: 기간, 상태)
getStoreOrderDetail(accountId, storeId, orderId) | StoreOrderDetailResponseDto | readOnly | Ownership | 사장용 주문 상세
confirmOrder(accountId, storeId, orderId) | void | REQUIRED | Ownership + Event | 주문 확정 → 사용자에게 알림 발송

«Helper» OrderAccessHelper
메서드명 | 반환타입 | 설명
---|---|---
verifyOrderOwnership(accountId, orderId) | Order | 주문 소유권 검증 (구매자) 본인 검증 → 실패 시 ForbiddenException
verifyStoreOrderAccess(accountId, storeId, orderId) | Order | 사장용: 본인 상점 + 해당 주문 검증
verifyCancellable(orderId) | Order | 취소 가능 상태 검증 (PENDING/PAID)
verifyRefundable(orderId) | Order | 환불 가능 상태 검증 (PAID, 환불 기간 내)
verifyPaymentOwnership(accountId, paymentId) | Payment | 결제 소유권 검증

### ● Repository

«Repository» OrderRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long orderId) | Optional<Order> | 주문 ID로 조회
findByIdAndAccountId(Long orderId, Long accountId) | Optional<Order> | 본인 주문 검증용
findByOrderNumber(String orderNumber) | Optional<Order> | 주문번호로 조회
findByIdForUpdate(Long orderId) | Optional<Order> | @Lock(PESSIMISTIC_WRITE) / 결제 처리·취소 시 동시성 제어
existsByOrderNumber(String orderNumber) | boolean | orderNumber 채번 시 충돌 검사용
findAllByAccountId(Long accountId, Pageable pageable) | Page<Order> | 사용자 주문 목록
findAllByAccountIdAndStatus(Long accountId, OrderStatus status, Pageable pageable) | Page<Order> | 상태별 사용자 주문 목록
findAllByAccountIdAndStatusIn(Long accountId, List<OrderStatus> statuses, Pageable pageable) | Page<Order> | 다중 상태 필터 사용자 주문 조회 (예: PAID + REFUND_REQUESTED 동시 조회)
findAllByAccountIdAndCreatedAtBetween(Long accountId, LocalDateTime start, LocalDateTime end, Pageable pageable) | Page<Order> | 기간별 사용자 주문
findAllByStoreId(Long storeId, Pageable pageable) | Page<Order> | 사장용 상점 주문 목록
findAllByStoreIdAndStatus(Long storeId, OrderStatus status, Pageable pageable) | Page<Order> | 사장용 상태별 조회
findAllByStoreIdAndStatusIn(Long storeId, List<OrderStatus> statuses, Pageable pageable) | Page<Order> | 다중 상태 필터 사장용 주문 조회
findAllByStoreIdAndCreatedAtBetween(Long storeId, LocalDateTime start, LocalDateTime end, Pageable pageable) | Page<Order> | 사장용 기간별 매출 조회
countByStoreIdAndStatus(Long storeId, OrderStatus status) | Long | 사장 대시보드 집계용
sumTotalPriceByStoreIdAndStatusAndPaidAtBetween(Long storeId, OrderStatus status, LocalDateTime start, LocalDateTime end) | BigDecimal | 기간별 매출 합계 (사장 대시보드)

«Repository» OrderItemRepository
메서드명 | 반환타입 | 설명
---|---|---
findAllByOrderId(Long orderId) | List<OrderItem> | 주문에 포함된 상품 목록
findAllByOrderIdIn(List<Long> orderIds) | List<OrderItem> | 다수 주문의 상품 일괄 조회 (N+1 방지)
findAllByOrderIdInWithProduct(List<Long> orderIds) | List<OrderItem> | Product 동시 페치 @EntityGraph(attributePaths={"product"})
countByProductId(Long productId) | Long | 상품 판매 횟수 (인기 상품 집계)
findTopProductsByStoreId(Long storeId, int limit) | List<ProductSalesDto> | 상점 인기 상품 TOP-N (대시보드용)
existsByAccountIdAndProductIdAndStatus(Long accountId, Long productId, OrderStatus status) | boolean | 사용자가 특정 상품을 구매했는지 검증 (리뷰 작성 권한 등)

«Repository» PaymentRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long paymentId) | Optional<Payment> | 결제 ID로 조회
findByIdAndAccountId(Long paymentId, Long accountId) | Optional<Payment> | 본인 결제 검증용
findByOrderId(Long orderId) | Optional<Payment> | 주문에 연결된 결제 조회 (1:1)
findByPortOnePaymentId(String portOnePaymentId) | Optional<Payment> | PortOne ID로 조회 (Webhook 중복 확인)
findByIdempotencyKey(String idempotencyKey) | Optional<Payment> | 멱등성 키로 중복 결제 방지
existsByIdempotencyKey(String idempotencyKey) | boolean | 중복 처리 여부 확인
existsByPortOnePaymentId(String portOnePaymentId) | boolean | Webhook 중복 수신 확인
findAllByAccountId(Long accountId, Pageable pageable) | Page<Payment> | 사용자 결제 내역
findAllByAccountIdAndStatus(Long accountId, PaymentStatus status, Pageable pageable) | Page<Payment> | 상태별 사용자 결제 내역
findAllByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime threshold) | List<Payment> | 미완결 결제 정리용 (스케줄러)
countByStatusAndCreatedAtBetween(PaymentStatus, LocalDateTime, LocalDateTime) | Long | PortOne Webhook 처리 결과 모니터링 (관리자 대시보드)

«Repository» CartRepository
메서드명 | 반환타입 | 설명
---|---|---
findByAccountIdAndStoreId(Long accountId, Long storeId) | Optional<Cart> | 특정 가게의 장바구니 조회
findAllByAccountId(Long accountId) | List<Cart> | 다른storeId 기존 장바구니 정리용
existsByAccountIdAndStoreId(Long accountId, Long storeId) | boolean | 특정 가게 장바구니 존재 여부
deleteByAccountIdAndStoreId(Long accountId, Long storeId) | void | 장바구니 삭제
deleteAllByAccountId(Long accountId) | void | 회원 탈퇴 시CASCADE

«Repository» CartItemRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long cartItemId) | Optional<CartItem> | 장바구니 항목 조회
findByIdAndCart_AccountId(Long cartItemId, Long accountId) | Optional<CartItem> | 본인 장바구니 항목 검증용
findAllByCartId(Long cartId) | List<CartItem> | 장바구니의 모든 항목
findByCartIdAndProductIdAndEventProductId(Long cartId, Long productId, Long eventProductId) | Optional<CartItem> | 일반/이벤트 상품 별도 카트 항목
existsByCartIdAndProductIdAndEventProductId(Long cartId, Long productId, Long eventProductId) | boolean | 상품 중복 확인
countByCartId(Long cartId) | Long | 장바구니 상품 수
deleteAllByCartId(Long cartId) | void | 장바구니 전체 비우기
deleteByCartIdAndProductIdAndEventProductId(Long cartId, Long productId, Long eventProductId) | void | 특정 조합 카트 항목 제거

## 5.8 ENUM (Order/Payment 관련)

«Enum» OrderStatus
ENUM 값 | 설명
---|---
PENDING | 결제 대기
PAID | 결제 완료
FAILED | 결제 실패
CANCELLED | 주문 취소
REFUNDED | 환불 완료

«Enum» PaymentStatus
ENUM 값 | 설명
---|---
PENDING | 결제 대기
PAID | 결제 완료
FAILED | 결제 실패
CANCELLED | 결제 취소
REFUNDED | 환불 완료

«Enum» PaymentMethod
ENUM 값 | 설명
---|---
CARD | 신용/체크카드
KAKAOPAY | 카카오페이
NAVERPAY | 네이버페이
TOSS | 토스

> 원문 표 형식 주의: 5.8절 원문에서 PaymentMethod 값(CARD/KAKAOPAY/NAVERPAY/TOSS)이 "PaymentStatus" 표 제목 아래 잘못 표기되어 있음(표 헤더 중복 오류). 원문 그대로 보존하되 의미상 PaymentMethod ENUM으로 분리 표기함.

## 8. 설계 시퀀스 다이어그램 (관련)

- DSEQ-12: 장바구니 담기 (옵션)
- DSEQ-13: 주문 생성 + 결제 (PortOne)
- DSEQ-14: PortOne Webhook 처리
- DSEQ-15: 주문 취소 + 환불

[다이어그램 원본은 기존 SDD 문서 참조]

## 9.2 도메인 ERD (참조)

● Order / Payment 도메인
[다이어그램 원본은 기존 SDD 문서 참조]

## 9.3 테이블 정의

### Order (주문)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
order_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 주문 고유 식별자
account_id | bigint | FK(account), NOT NULL | 구매자
store_id | bigint | FK(store), NOT NULL | 주문 상점
total_price | decimal(10,2) | NOT NULL | 총 결제 금액
order_number | varchar(50) | UNIQUE, NOT NULL | 사용자 표시용 주문번호
version | bigint | NOT NULL | 낙관적 락(@Version)
status | varchar(20) | NOT NULL | OrderStatus ENUM
paid_at | datetime | nullable | 결제 완료 시점
cancelled_at | datetime | nullable | 취소 시점
cancel_reason | varchar(500) | nullable | 취소 사유
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### OrderItem (주문 상품)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
order_item_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 주문 상품 고유 식별자
order_id | bigint | FK(order), NOT NULL | 연관 주문
product_id | bigint | FK(product), nullable | 연관 상품
event_product_id | bigint | FK(event_product), nullable | 연관 이벤트 상품
product_name | varchar(100) | NOT NULL | 주문 시점 상품명 스냅샷
selected_options | text | NOT NULL | 주문 시 선택된 옵션 스냅샷(JSON)
quantity | int | NOT NULL, ≥1 | 수량
price | decimal(10,2) | NOT NULL | 주문 시점 가격 고정

> 참고: `OrderItem`, `Location`, `Region`(account.md)을 제외한 모든 엔티티는 `BaseEntity` 상속이 원칙이나(CLAUDE.md 절대 규칙), 원문 테이블 정의에는 OrderItem에 created_at/modified_at 컬럼이 없음 — 원문 그대로 보존.

### Payment (결제)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
payment_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 결제 고유 식별자
order_id | bigint | FK(order), UNIQUE, NOT NULL | 연관 주문(1:1)
account_id | bigint | FK(account), NOT NULL | 결제자
portone_payment_id | varchar(100) | UNIQUE, NOT NULL | PortOne 결제 고유 번호
idempotency_key | varchar(100) | UNIQUE, NOT NULL | 중복 결제 방지(멱등성 키)
amount | decimal(10,2) | NOT NULL | 결제 금액
status | varchar(20) | NOT NULL | PaymentStatus ENUM
pg_provider | varchar(50) | nullable | PG사 이름
payment_method | varchar(20) | NOT NULL | PaymentMethod ENUM
paid_at | datetime | nullable | 결제 완료 시점
cancelled_at | datetime | nullable | 취소 시점
fail_reason | varchar(500) | nullable | 결제 실패 사유(PortOne 응답)
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### Cart (장바구니)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
cart_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 장바구니 고유 식별자
account_id | bigint | FK(account), UNIQUE, NOT NULL | 회원
store_id | bigint | FK(store), NOT NULL | 동일 상점 제약 관리
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### CartItem (장바구니 상품)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
cart_item_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 장바구니 상품 고유 식별자
cart_id | bigint | FK(cart), NOT NULL | 연관 장바구니
product_id | bigint | FK(product), nullable | 연관 상품
event_product_id | bigint | FK(event_product), nullable | 연관 이벤트 상품
selected_option_item_ids | text | nullable | 선택한 옵션ID 목록(JSON)
options_total_price | decimal(10,2) | NOT NULL, DEFAULT 0 | 옵션 추가 가격 총합
selected_options_hash | varchar(64) | NOT NULL | 옵션 조합 구분용 해시값
quantity | int | NOT NULL, ≥1 | 수량
price | decimal(10,2) | NOT NULL | 추가 당시 가격 고정

## 중복 참조 (common.md / 다른 도메인에도 기재됨)

- Redis 분산 락 키 패턴: `order:product:{productId}`(3s), `order:products:{1-3-5}`(3s), `payment:order:{orderId}`(10s), `payment:webhook:{portOnePaymentId}`(30s)는 `common.md` 6.1.2 참조
- 비관적 락 적용: `ProductRepository.findByIdForUpdate`(재고 차감, store.md), `OrderRepository.findByIdForUpdate`(결제 처리/취소)는 `common.md` 6.1.3 참조
- 낙관적 락 적용 대상 Entity(Orders 등)는 `common.md` 6.1.4 참조
- 멱등성: Idempotency-Key 패턴(결제 요청), PortOne Webhook UNIQUE 제약 기반 멱등성은 `common.md` 6.5 참조
- PortOne Webhook 보안 처리(서명 검증, 리플레이 공격 방지)는 `common.md` 6.9.2 참조
- 이벤트 카탈로그: OrderCreatedEvent, OrderConfirmedEvent, OrderCancelledEvent, PaymentCompletedEvent는 `common.md` 6.2.5 참조
- `OrderExpirationScheduler`(1분 주기, PENDING 15분 경과 주문 만료 처리)는 CLAUDE.md 스케줄러 목록 기준이며 SDD 원문에는 명시되어 있지 않음 — 코드베이스 구현 기준으로 추가 참고.
