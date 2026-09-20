import { apiClient } from '../apiClient';

export const approvalApi = {
  // 사장 가입 신청 목록 / 상세 조회
  // params: approvalStatus(PENDING|APPROVED|REJECTED, 생략 시 PENDING), page, size 등
  getApplications: (params) => {
    return apiClient.get('/admin/accounts/owners/applications', { params });
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
