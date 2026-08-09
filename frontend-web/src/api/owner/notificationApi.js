import { apiClient, currentAccessToken } from '../apiClient';

export const notificationApi = {
  // SSE 연결에 필요한 BaseURL과 메모리에 저장된 AccessToken 추출
  getSubscribeInfo: () => {
    // apiClient의 baseURL이 상대경로이거나 없을 경우를 대비한 fallback
    const baseURL = apiClient.defaults.baseURL || 'http://localhost:8080';

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
