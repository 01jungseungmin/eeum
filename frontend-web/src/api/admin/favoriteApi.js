import { apiClient } from '../apiClient';

export const favoriteApi = {
  // 찜 인기 항목 통계 (기간 + refType 기준 상위 N개)
  getStats: (params) => {
    return apiClient.get('/admin/favorites/stats', { params });
  },

  // favoriteCount 정합성 재계산 (refType 생략 시 전체)
  recalculate: (refType) => {
    return apiClient.post('/admin/favorites/recalculate', null, {
      params: refType ? { refType } : {},
    });
  },
};
