import { apiClient } from '../apiClient';

// 관리자 알림 발송 / 이력 조회 API (AdminNotificationController)
export const notificationApi = {
  // 시스템 공지 발송 (전체 또는 특정 accountId 목록)
  sendSystemNotice: (payload) =>
    apiClient.post('/admin/notifications/system', payload),

  // 이벤트/마케팅 알림 발송 (마케팅 수신 동의자에게만)
  sendEventNotice: (payload) =>
    apiClient.post('/admin/notifications/event', payload),

  // 발송 이력 조회 (type/accountId/기간 필터)
  getHistory: (params) => apiClient.get('/admin/notifications', { params }),
};
