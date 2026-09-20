import { apiClient } from '../apiClient';

// 사장 정산 도메인 관련 API 함수 모음집
// 백엔드: OwnerSettlementController (/owner/settlements/**)
export const settlementApi = {
  // 결제 건별 수익 원장 목록 (결제금액/PG수수료/플랫폼수수료/정산금액/상태)
  getRevenues: (params) =>
    apiClient.get('/owner/settlements/revenues', { params }),

  // 주간 정산(지급) 목록 (기간/정산금액/상태/지급완료일)
  getWeeklySettlements: (params) =>
    apiClient.get('/owner/settlements/weekly', { params }),
};
