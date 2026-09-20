import { apiClient } from '../apiClient';

// 관리자 정산 지급 API (AdminSettlementController)
export const settlementApi = {
  // 주간 정산 목록 조회 (전체 매장)
  getWeeklySettlements: (params) =>
    apiClient.get('/admin/settlements', { params }),

  // 지급 작업 선점 (claim). 성공 시 반환되는 claimToken을 완료 처리에 그대로 넘겨야 한다.
  claimPayout: (weeklySettlementId) =>
    apiClient.post(`/admin/settlements/${weeklySettlementId}/claim`),

  // 수동 지급 완료 처리
  completePayout: (weeklySettlementId, { claimToken, payoutReference }) =>
    apiClient.post(`/admin/settlements/${weeklySettlementId}/complete`, {
      claimToken,
      payoutReference,
    }),

  // 지급을 막고 있는 미완료 취소 작업 조회
  getBlockingCancellations: (weeklySettlementId) =>
    apiClient.get(
      `/admin/settlements/${weeklySettlementId}/blocking-cancellations`,
    ),

  // 확정된 전액 취소의 내부 반영 재시도
  reconcileConfirmedCancellation: (weeklySettlementId, orderId) =>
    apiClient.post(
      `/admin/settlements/${weeklySettlementId}/blocking-cancellations/${orderId}/reconcile`,
    ),

  // 부분 취소 누적 금액 대사
  reconcilePartialCancellation: (weeklySettlementId, orderId, body) =>
    apiClient.post(
      `/admin/settlements/${weeklySettlementId}/blocking-cancellations/${orderId}/partial-reconcile`,
      body,
    ),

  // 누락 수익 원장을 원래 주차로 재마감
  recoverLateRevenue: (ownerRevenueId) =>
    apiClient.post(`/admin/settlements/late-revenues/${ownerRevenueId}/recover`),
};
