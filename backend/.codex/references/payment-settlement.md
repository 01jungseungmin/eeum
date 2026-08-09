# 결제·정산 검토 기준

결제, 환불, 취소, 매출 원장, 주간 정산, PortOne 연동을 검토할 때 사용하는 단일 기준 문서다.
상세 규칙을 다른 Skill에 복사하지 말고, 관련 Skill이 이 문서를 직접 읽도록 연결한다.

## 적용 범위

다음 중 하나라도 해당하면 이 문서를 처음부터 끝까지 읽는다.

- `order`, `payment`, `refund`, `cancellation`, `revenue`, `settlement` 코드 변경
- PortOne Webhook, 결제 조회, 취소, 파트너 정산 API 변경
- 결제·정산 전체 리뷰 또는 동시성 검사 요청

전체 리뷰 범위는 Controller에서 끝나지 않는다. 다음 경로를 모두 추적한다.

`Controller → Service/Processor → Entity → Repository → Scheduler/Runner → PortOne Gateway → 복구/알림 → 테스트`

## 상태 전이

### 내부 상태

- 주문: `PENDING → PAID → CONFIRMED → READY → COMPLETED`
- 주문 종료: `PENDING/PAID/CONFIRMED/READY → CANCELLED`, `PENDING → EXPIRED`
- 결제: `PENDING → PAID`, `NOT_PAID → PAID`, `PAID → PARTIALLY_REFUNDED → CANCELLED/REFUNDED`
- 결제 종료: `PENDING/NOT_PAID/PAID/PARTIALLY_REFUNDED → CANCELLED`
- 환불: `null/REJECTED → REQUESTED → APPROVED/REJECTED`
- 취소 작업: `PENDING → PG_CANCEL_REQUESTED → PG_CANCELLED → COMPLETED`
  - PG가 즉시 완료를 반환하면 `PENDING → PG_CANCELLED`로 전이할 수 있다.
- 취소 작업 실패: `PENDING/PG_CANCELLED → MANUAL_REVIEW_REQUIRED`
- 수익 원장: `ACCRUED → SETTLEMENT_PENDING → SETTLED`, 취소 시 `→ CANCELLED`
- 주간 정산: `PAYOUT_PENDING → PAYOUT_IN_PROGRESS → COMPLETED`
- 주간 정산 실패: `PAYOUT_IN_PROGRESS → FAILED → MANUAL_REVIEW_REQUIRED`

상태 enum만 보지 말고 모든 진입점이 같은 전이 규칙을 사용하는지 확인한다.

### PortOne 외부 상태

외부 상태와 JSON 구조는 리뷰 시점의 PortOne 공식 문서에서 다시 확인한다.
이 문서의 상태 목록만 근거로 구현을 승인하지 않는다.

- 결제 조회: `PAID`, `VIRTUAL_ACCOUNT_ISSUED`, `CANCELLED`, `PARTIAL_CANCELLED`, `FAILED` 등
- 취소 응답: `SUCCEEDED`, `REQUESTED`, `FAILED`
- Platform Transfer와 Partner Settlement/Payout은 서로 다른 상태 enum과 식별자를 사용한다.
- 테스트 모드와 운영 모드는 동일한 완료 API와 상태 전이를 사용한다고 가정하지 않는다.

공식 기준:

- Webhook: https://developers.portone.io/opi/ko/integration/webhook/readme-v2?v=v2
- 결제·취소: https://developers.portone.io/api/rest-v2/payment
- 취소 가이드: https://developers.portone.io/opi/ko/integration/cancel/v2/readme
- Platform Transfer: https://developers.portone.io/api/rest-v2/platform.transfer?v=v2
- Partner Settlement: https://developers.portone.io/api/rest-v2/platform.partnerSettlement?v=v2
- Payout: https://developers.portone.io/api/rest-v2/platform.payout

## 금전 불변식

아래 조건이 하나라도 증명되지 않으면 결제·정산 리뷰를 통과시키지 않는다.

1. 하나의 성공 결제에는 하나의 수익 원장만 존재한다.
2. PG 결제가 완료되지 않은 주문은 수익 원장과 정산 대상이 될 수 없다.
3. 취소·환불된 금액은 사장에게 지급되지 않는다.
4. 사장에게 지급된 금액을 환불해야 한다면 자동 차단 또는 과지급 수동 확인 기록이 남는다.
5. PG 취소가 `REQUESTED`인 상태를 취소 완료로 확정하지 않는다.
6. 외부 API 성공 후 내부 DB 실패는 멱등 재시도 또는 수동 확인으로 수렴한다.
7. 재고 차감과 복원은 주문당 각각 필요한 횟수만 수행된다.
8. 정산 지급 결과는 현재 claim/fencing token을 가진 작업자만 반영한다.
9. 정산 금액은 포함된 수익 원장의 순액 합계와 일치한다.
10. 테스트 모드 성공이 운영 모드 성공으로 오인되지 않는다.

## Webhook 계약

- raw body를 파싱 전에 보존한다.
- PortOne이 전달한 Standard Webhooks 헤더 전체를 검증기에 전달한다.
- 단일 임의 HMAC 헤더 구현을 공식 계약과 대조 없이 사용하지 않는다.
- `2024-04-25` 형식의 `type`, `timestamp`, `data.paymentId` 구조를 처리한다.
- Webhook 이벤트 본문만 신뢰하지 않고 결제 단건 조회 결과와 내부 주문 금액을 대조한다.
- Webhook과 브라우저 verify의 도착 순서는 보장되지 않는다고 가정한다.
- 동일 이벤트 재전송과 서로 다른 이벤트의 동일 paymentId 처리를 구분한다.
- `Transaction.Paid`, 취소, 부분 취소, 가상계좌 발급 등 지원 이벤트를 명시한다.

## 취소·환불 계약

- 취소 API 응답 본문을 버리지 않고 취소 상태와 취소 식별자를 저장·판단한다.
- `SUCCEEDED`만 PG 취소 완료로 확정한다.
- `REQUESTED`는 Webhook 또는 결제 재조회로 최종 상태를 조정한다.
- 외부 콘솔 취소와 부분 취소도 내부 Payment, OwnerRevenue, WeeklySettlement에 반영한다.
- 부분 취소는 PortOne의 누적 취소 금액을 저장하고, 직전 누적 금액과의 차액만 수익·정산에서 차감한다.
- 부분 취소 뒤 내부 취소를 요청할 때는 원 결제액이 아니라 남은 취소 가능 금액을 사용한다.
- 지급 전 부분 취소는 수익·정산 금액을 함께 줄이고, 지급 시작 이후 부분 취소는 지급 스냅샷을 덮어쓰지 않고 수동 확인과 과지급 알림으로 격리한다.
- 가상계좌 환불에 필요한 환불 계좌 정보를 지원하거나 가상계좌 결제를 API에서 차단한다.
- 현재 API에는 고객 환불 계좌 입력 계약이 없으므로 신규 `VIRTUAL_ACCOUNT` 주문을 차단한다. 기존 가상계좌 결제의 외부 취소 Webhook 조정은 허용한다.
- 외부 호출은 긴 DB 트랜잭션과 비관적 락 안에서 수행하지 않는다.

## 정산 계약

- Transfer 생성 성공과 실제 지급 완료를 구분한다.
- Transfer, Partner Settlement, Payout의 상태와 ID를 서로 바꿔 사용하지 않는다.
- 운영 모드에서도 실제 지급 완료 상태를 재조회해 내부 `COMPLETED`로 수렴시킨다.
- 외부 transfer가 생성된 뒤 내부 금액이 바뀌면 자동 지급을 중단하고 금액 불일치를 격리한다.
- 지급 재시도는 고정된 외부 ID와 멱등키를 사용한다.
- claim 임대 만료 뒤 이전 작업자의 성공·실패 결과를 반영하지 않는다.

## 락과 트랜잭션

- 동일 주문의 verify, Webhook, 취소, 환불, 사장 상태 변경은 같은 주문 락 키를 사용한다.
- 권장 순서는 `Redis 주문 락 → 짧은 DB 트랜잭션 → 커밋 → Redis 락 해제`다.
- DB 락 순서는 모든 경로에서 동일하게 유지한다.
- 정산 관련 취소는 `WeeklySettlement → OwnerRevenue` 순서로 잠근다.
- 외부 호출 전 prepare/claim 상태를 커밋하고, 외부 호출 후 별도 트랜잭션에서 결과를 반영한다.
- 작업 상태는 락 획득 후 다시 조회한다.

## 필수 계약 테스트

실제 PortOne 운영 API를 호출하거나 Secret을 사용하지 않는다. 공식 예제 payload와 로컬 HTTP Stub으로 검증한다.

- Standard Webhooks 정상·잘못된 서명, 중첩 `data.paymentId`
- Webhook 선처리 후 브라우저 verify 멱등 성공
- 취소 `SUCCEEDED`, `REQUESTED`, `FAILED` 응답 매핑
- 외부 `CANCELLED`, `PARTIAL_CANCELLED` 상태 조정
- 부분 취소 누적 금액의 중복 Webhook 멱등 처리와 남은 금액 취소
- 신규 `VIRTUAL_ACCOUNT` 주문 차단
- 가상계좌를 다시 지원할 경우 `VIRTUAL_ACCOUNT_ISSUED`, 환불 계좌 전달, 입금기한 이후 입금
- 테스트 모드와 운영 모드의 Transfer/Partner Settlement/Payout 상태 매핑
- 지급 claim 만료 전후 오래된 결과 fencing
- 환불 승인과 지급 시작의 양방향 경합
- 현장결제 취소의 최종 Payment 상태

계약 테스트는 요청 method/path/header/body와 응답 JSON→내부 상태 매핑을 모두 검증한다.
DTO fixture만 역직렬화하는 테스트는 어댑터 계약을 보호하지 못하므로 충분하지 않다.
