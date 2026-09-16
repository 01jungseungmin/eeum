# 주문/결제 동시성 — 알려진 경쟁 시나리오

`concurrency-auditor`가 주문/결제 도메인 감사 시 확인하는 최소 점검 목록이다.
이 목록은 하한선일 뿐이며, 목록 밖 시나리오도 코드에서 도출해야 한다.
결제·정산 전체 상태와 PortOne 계약은 `../payment-settlement.md`를 함께 읽는다.

## 상태 전이

- 주문: `PENDING → PAID → CONFIRMED → READY → COMPLETED`, `PENDING/PAID → CANCELLED`, `PENDING → EXPIRED`
- 결제: `PENDING → PAID`, `PENDING/PAID → CANCELLED`, `PAID → REFUNDED`
- 환불: `REQUESTED → APPROVED / REJECTED`

## 주문 생성 vs 재고 차감

- 동일 상품을 여러 사용자가 동시에 주문할 때 재고가 음수가 되지 않는가?
- Product와 EventProduct 모두 락이 적용되는가?
- 장바구니 가격과 주문 스냅샷 가격이 동시 변경에 안전한가?

## 결제 Webhook vs 주문 만료 스케줄러

`Webhook: PENDING → PAID`와 `Scheduler: PENDING → EXPIRED`가 같은 주문을 동시에 변경할 수 있다.

- 둘 다 같은 order/payment에 대해 lock 또는 조건부 update를 사용하는가?
- 만료된 주문에 뒤늦게 성공 Webhook이 오면 어떻게 처리되는가?
- 이미 PAID가 된 주문을 스케줄러가 EXPIRED로 바꾸지 않는가?
- EXPIRED 처리 후 재고 복구와 Webhook 결제 성공 처리가 충돌하지 않는가?

## 사장 거절 vs 고객 취소

- 두 요청이 동시에 같은 주문을 취소할 수 있는가?
- 재고 복구가 두 번 일어나지 않는가?
- `Payment.cancel()`이 두 번 호출되어도 안전한가?

## 환불 승인 vs 환불 거절

- 승인과 거절이 동시에 요청될 수 있는가?
- `RefundStatus.REQUESTED` 검증 후 상태 변경까지 락으로 보호되는가?
- PortOne 환불 API가 중복 호출되지 않는가?

## PortOne 외부 API 호출

- 외부 API 호출 전후 상태 변경 순서를 확인한다.
- 외부 API 성공 후 DB 저장 실패 시 보상 로직이 있는가?
- 같은 결제 건에 대해 중복 환불이 발생하지 않는가?
- Webhook은 같은 paymentId가 여러 번 와도 1회만 상태 변경되는가? (Redis + DB Unique 이중 방어)
- Webhook과 브라우저 verify가 어느 순서로 도착해도 같은 성공 결과로 수렴하는가?
- 취소 `REQUESTED` 상태에서 내부 취소를 완료하지 않는가?
- 외부 콘솔 취소와 부분 취소가 수익 원장·정산에 반영되는가?
- 가상계좌 발급·입금·주문 만료가 서로 다른 상태로 조정되는가?

## 환불 승인 vs 정산 지급

- 환불 승인 prepare와 지급 claim 중 어느 쪽이 먼저 시작돼도 과지급을 차단하는가?
- 미완료 취소 작업이 포함된 정산의 지급 시작을 차단하는가?
- 외부 transfer 생성 뒤 환불이 발생하면 자동 지급 중단 또는 과지급 경고가 남는가?
- claim 임대 만료 뒤 이전 작업자의 결과가 최신 결과를 덮어쓰지 않는가?

## 대응 통합 테스트

- IT-ORD-003: 동일 상품 동시 100명 주문 → 재고 정확 차감
- IT-ORD-004: 동일 PortOne Webhook 2회 수신 → 1회만 처리
- IT-ORD-005: 주문 만료 스케줄러와 결제 Webhook 동시 처리 → 최종 상태 1개로 수렴
- IT-ORD-006: 고객 취소와 사장 거절 동시 처리 → 재고 1회만 복구
- IT-ORD-007: 환불 승인 중복 요청 → PortOne 환불 1회만 호출
- CT-PAY-001: Standard Webhooks header + `data.paymentId` 계약
- CT-PAY-002: 취소 `SUCCEEDED/REQUESTED/FAILED` 응답 매핑
- CT-PAY-003: 외부 취소/부분 취소 → 수익 원장·정산 조정
- CT-SET-001: 테스트/운영 Transfer·Partner Settlement 지급 완료 상태 매핑
