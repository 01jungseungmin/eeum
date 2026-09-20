import { apiClient } from '../apiClient';

export const accountApi = {
  // 내 정보 조회 (닉네임·이름 등, 사장/관리자 공통)
  getMyInfo: () => {
    return apiClient.get('/accounts/me');
  },

  // 내 사업자 정보 조회
  getOwnerAccountInfo: () => {
    return apiClient.get('/accounts/me/owner');
  },

  // 사업자 정보(사업자번호) 수정 — 승인 완료 후에는 변경 불가
  updateOwnerAccountInfo: (payload) => {
    return apiClient.put('/accounts/me/owner', payload);
  },
};
