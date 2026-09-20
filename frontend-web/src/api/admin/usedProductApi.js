import { apiClient } from '../apiClient';

// 관리자 중고거래 관리 API (AdminUsedProductController)
export const usedProductApi = {
  // 중고 게시글 목록 조회 (숨김/삭제 포함, 최신순)
  getUsedProducts: (params) => apiClient.get('/admin/used', { params }),

  // 중고 게시글 상세 조회
  getUsedProductDetail: (usedProductId) =>
    apiClient.get(`/admin/used/${usedProductId}`),

  // 게시글 숨김 / 숨김 해제
  hideUsedProduct: (usedProductId) =>
    apiClient.patch(`/admin/used/${usedProductId}/hide`),
  showUsedProduct: (usedProductId) =>
    apiClient.patch(`/admin/used/${usedProductId}/show`),
};
