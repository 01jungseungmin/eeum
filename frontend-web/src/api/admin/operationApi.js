import { apiClient } from '../apiClient';

export const operationApi = {
  // 운영 실패 이력 목록 (category/from/to/keyword 필터, 페이지네이션)
  getFailures: (params) => {
    return apiClient.get('/admin/operations/failures', { params });
  },

  // 운영 현황 요약 (미처리 신고/문의 수, 구간 내 실패 건수, 최근 실패 10건)
  getSummary: (hours) => {
    return apiClient.get('/admin/operations/summary', {
      params: hours ? { hours } : {},
    });
  },
};
