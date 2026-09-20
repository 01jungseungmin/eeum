// 사장 매출/정산 페이지 도메인 상수 (백엔드 enum과 1:1 매칭)
// domain/settlement/enums/OwnerRevenueStatus.java
export const OWNER_REVENUE_STATUS_LABEL = {
  ACCRUED: '정산 대기 전',
  SETTLEMENT_PENDING: '정산 대기중',
  SETTLED: '정산 완료',
  CANCELLED: '취소됨',
};

// domain/settlement/enums/WeeklySettlementStatus.java
export const WEEKLY_SETTLEMENT_STATUS_LABEL = {
  PAYOUT_PENDING: '지급 대기',
  PAYOUT_IN_PROGRESS: '지급 처리중',
  COMPLETED: '지급 완료',
  FAILED: '지급 실패',
  MANUAL_REVIEW_REQUIRED: '수동 확인 필요',
};

// 매출 집계에서 제외할 상태 (취소된 원장은 매출/수수료 합계에 포함하지 않음)
export const OWNER_REVENUE_EXCLUDED_FROM_TOTAL = ['CANCELLED'];

// 주간 정산 중 아직 끝나지 않은(진행중인) 상태 — "정산 대기" 카드/배너에 사용
export const WEEKLY_SETTLEMENT_IN_PROGRESS_STATUSES = [
  'PAYOUT_PENDING',
  'PAYOUT_IN_PROGRESS',
  'MANUAL_REVIEW_REQUIRED',
];

// 관리자 지급 화면에서 "지급 처리" 액션을 노출할 수 있는 상태
export const WEEKLY_SETTLEMENT_CLAIMABLE_STATUSES = [
  'PAYOUT_PENDING',
  'MANUAL_REVIEW_REQUIRED',
];

// domain/order/enums/PaymentCancellationStatus.java (관리자 정산 지급 차단 사유 조회용)
export const PAYMENT_CANCELLATION_STATUS_LABEL = {
  PENDING: '취소 대기',
  PG_CANCEL_REQUESTED: 'PG 취소 요청됨',
  PG_CANCELLED: 'PG 취소 확정 (내부 반영 대기)',
  COMPLETED: '취소 완료',
  MANUAL_REVIEW_REQUIRED: '수동 확인 필요',
};
