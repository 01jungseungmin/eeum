import { apiClient } from '../apiClient';

export const sanctionApi = {
  // 회원 제재 이력 조회
  getAccountHistories: (accountId, page = 0, size = 20) => {
    return apiClient.get(`/admin/sanction-histories/accounts/${accountId}`, {
      params: { page, size },
    });
  },

  // 상점 제재 이력 조회
  getStoreHistories: (storeId, page = 0, size = 20) => {
    return apiClient.get(`/admin/sanction-histories/stores/${storeId}`, {
      params: { page, size },
    });
  },
};
