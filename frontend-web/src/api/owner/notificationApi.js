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

  readNotification: (notificationId) => {
    return apiClient.patch(`/notifications/read/all`);
  },
};
