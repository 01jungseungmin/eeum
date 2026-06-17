import { client } from './client';

// DB 컬럼 구조를 바탕으로 한 알림 아이템 인터페이스
export interface NotificationItem {
  notificationId: number;
  accountId: number;
  type: string;           // 예: 'NEW_ORDER', 'PAYMENT_COMPLETED'
  title: string;          // 예: '결제가 완료되었습니다'
  content: string;        // 예: '맛있는 반찬가게 주문 결제가...'
  refType: string;        // 예: 'ORDER', 'PAYMENT'
  refId: string;
  linkUrl: string;        // 예: '/orders/22'
  Read: boolean | number | string; // DB의 '0', '1'이 boolean으로 오는지 확인 필요
  readAt?: string | null;
  createdAt: string;
}

export const notificationApi = {
  // 1. 내 알림 목록 조회 (페이징 & 필터 적용 가능)
  getNotifications: async (category?: string, page: number = 0) => {
    let url = `/notifications?page=${page}&size=20`;
    if (category && category !== 'ALL') {
      url += `&category=${category}`;
    }
    const response = await client.get(url);
    return response.data;
  },

  // 2. 알림 단건 읽음 처리
  markAsRead: async (notificationId: number) => {
    const response = await client.patch(`/notifications/${notificationId}/read`);
    return response.data;
  },

  // 3. 전체 읽음 처리
  markAllAsRead: async () => {
    const response = await client.patch('/notifications/read/all');
    return response.data;
  },

  // 4. 알림 단건 삭제
  deleteNotification: async (notificationId: number) => {
    const response = await client.delete(`/notifications/${notificationId}`);
    return response.data;
  },

  // 5. 알림 전체 삭제
  deleteAllNotifications: async () => {
    const response = await client.delete('/notifications/all');
    return response.data;
  },

  // 6. 안 읽은 알림 수 조회 (앱 하단 뱃지용)
  getUnreadCount: async () => {
    const response = await client.get('/notifications/unread/count');
    return response.data;
  },

  // 7. 알림 설정 조회
  getSettings: async () => {
    const response = await client.get('/notifications/settings');
    return response.data;
  },

  // 8. 알림 설정 변경
  updateSettings: async (settingsData: any) => {
    const response = await client.patch('/notifications/settings', settingsData);
    return response.data;
  },

  updateFcmToken: async (fcmToken: string) => {
    const response = await client.put('/accounts/me/fcm-token', { fcmToken });
    return response.data;
  }
};