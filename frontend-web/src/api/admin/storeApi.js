import { apiClient } from '../apiClient';

// 관리자 상점 관리 API (AdminStoreController)
export const storeApi = {
  // 상점 목록 조회 (keyword/status는 선택, 서버 페이지네이션)
  getStores: (params) => apiClient.get('/admin/stores', { params }),

  // 상점 상세 조회
  getStoreDetail: (storeId) => apiClient.get(`/admin/stores/${storeId}`),

  // 상점 정지 / 정지 해제
  suspendStore: (storeId) =>
    apiClient.patch(`/admin/stores/${storeId}/suspend`),
  activateStore: (storeId) =>
    apiClient.patch(`/admin/stores/${storeId}/activate`),
};
