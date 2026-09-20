import { apiClient } from '../apiClient';

export const memberApi = {
  // 회원 목록 조회 (서버 페이징)
  // params: { page, size, status: ACTIVE|SUSPENDED|WITHDRAWN, role: ROLE_USER|ROLE_OWNER|ROLE_ADMIN, keyword(이메일·닉네임·이름) }
  getMembers: (params) => {
    return apiClient.get('/admin/accounts', { params });
  },

  // 탈퇴 회원 목록 조회
  getWithdrawnMembers: (params) => {
    return apiClient.get('/admin/accounts/withdrawn', { params });
  },

  // 회원 상세 조회 (활동 지역·사장 정보 포함)
  getMemberDetail: (accountId) => {
    return apiClient.get(`/admin/accounts/${accountId}`);
  },

  // 단일 회원 정지 / 해제
  suspendAccount: (accountId) => {
    return apiClient.patch(`/admin/accounts/${accountId}/suspend`, {});
  },
  activateAccount: (accountId) => {
    return apiClient.patch(`/admin/accounts/${accountId}/activate`, {});
  },

  // 회원 강제 탈퇴 / 복구
  withdrawAccount: (accountId) => {
    return apiClient.delete(`/admin/accounts/${accountId}`);
  },
  cancelWithdrawal: (accountId) => {
    return apiClient.patch(
      `/admin/accounts/${accountId}/withdrawal/cancel`,
      {},
    );
  },

  // 선택 회원 일괄 처리
  bulkSuspend: (accountIds) => {
    const requests = accountIds.map((id) => memberApi.suspendAccount(id));
    return Promise.all(requests);
  },
  bulkActivate: (accountIds) => {
    const requests = accountIds.map((id) => memberApi.activateAccount(id));
    return Promise.all(requests);
  },
};
