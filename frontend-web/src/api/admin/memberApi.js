import { apiClient } from '../apiClient'; // 프로젝트 경로에 맞게 맞춰주세요

export const memberApi = {
  // 전체 회원 목록 조회
  getAllMembers: (page = 0, size = 1000) => {
    return apiClient.get(`/admin/accounts?page=${page}&size=${size}`);
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
