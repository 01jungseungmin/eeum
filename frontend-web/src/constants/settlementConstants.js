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
