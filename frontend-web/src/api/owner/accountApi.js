import { apiClient } from '../apiClient';

export const accountApi = {
  // 내 사업자 정보 조회
  getOwnerAccountInfo: () => {
    return apiClient.get('/accounts/me/owner');
  },
};
