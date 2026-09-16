import { apiClient, currentAccessToken } from '../apiClient';

export const notificationApi = {
  // SSE 연결에 필요한 BaseURL과 메모리에 저장된 AccessToken 추출
  getSubscribeInfo: () => {
    // 운영에서는 baseURL이 보통 상대경로(/api)다 — EventSource는 상대 URL을
    // 현재 페이지 origin 기준으로 알아서 resolve하므로 그대로 써도 된다.
    // apiClient.defaults.baseURL이 비어있는 예외적인 경우에만 로컬 fallback 사용.
    const baseURL = apiClient.defaults.baseURL || '/api';

    return {
      url: `${baseURL}/notifications/subscribe`,
      token: currentAccessToken,
    };
  },

  // 알림 목록 조회 (카테고리 필터링 및 페이징 지원)
  getNotifications: (category = 'ALL', page = 0, size = 20) => {
    const params = { page, size };
    if (category && category !== 'ALL') {
      params.category = category;
    }
    return apiClient.get('/notifications', { params });
  },

  // 전체 읽음 처리 / 개별 읽음 처리
  readNotification: () => {
    return apiClient.patch('/notifications/read/all');
  },
  readSingleNotification: (notificationId) => {
    return apiClient.patch(`/notifications/${notificationId}/read`);
  },

  // 알림 전체 삭제 / 개별 삭제
  deleteAllNotifications: () => {
    return apiClient.delete('/notifications/all');
  },
  deleteNotification: (notificationId) => {
    return apiClient.delete(`/notifications/${notificationId}`);
  },

  // 전체 알림 설정 조회 / 수정
  getNotificationSettings: () => {
    return apiClient.get('/notifications/settings');
  },
  updateNotificationSettings: (payload) => {
    return apiClient.patch('/notifications/settings', payload);
  },

  // 카테고리별 전체 읽음 처리
  readCategoryNotification: (category) => {
    return apiClient.patch(`/notifications/categories/${category}/read`);
  },
};
