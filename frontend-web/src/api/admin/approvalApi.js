import { apiClient } from '../apiClient';

export const approvalApi = {
  // 사장 가입 승인 대기 목록 / 상세 조회
  getApplications: () => {
    return apiClient.get('/admin/accounts/owners/applications');
  },
  getOwnerDetail: (ownerId) => {
    return apiClient.get(`/admin/accounts/owners/${ownerId}`);
  },

  // 사장 가입 승인 / 거부
  approveOwner: (ownerId) => {
    return apiClient.patch(`/admin/accounts/owners/${ownerId}/approve`, {});
  },
  rejectOwner: (ownerId, reason) => {
    return apiClient.patch(`/admin/accounts/owners/${ownerId}/reject`, {
      reason,
    });
  },
};
