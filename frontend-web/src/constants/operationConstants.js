// 관리자 운영 모니터링 도메인 상수 (domain/operation/enums/OperationFailureCategory.java)
export const OPERATION_FAILURE_CATEGORY_LABEL = {
  PAYMENT_WEBHOOK: '결제 Webhook',
  REFUND: '환불/결제취소',
  SCHEDULER: '스케줄러',
  EXTERNAL_API: '외부 API',
};

export const OPERATION_FAILURE_CATEGORY_FILTER_OPTIONS = [
  { value: '', label: '전체' },
  ...Object.entries(OPERATION_FAILURE_CATEGORY_LABEL).map(([value, label]) => ({
    value,
    label,
  })),
];

// 운영 현황 요약 집계 구간 선택지 (백엔드 hours 파라미터, 미전달 시 24)
export const OPERATION_SUMMARY_HOURS_OPTIONS = [
  { value: 24, label: '최근 24시간' },
  { value: 24 * 7, label: '최근 7일' },
  { value: 24 * 30, label: '최근 30일' },
];
