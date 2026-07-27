import { useState, useEffect, useCallback } from 'react';
import { chatApi } from '../api/owner/chatApi';

export const useNotificationCounts = () => {
  const [counts, setCounts] = useState({ chat: 0 });

  const fetchUnreadCount = useCallback(async () => {
    try {
      const res = await chatApi.getUnreadCount();
      if (res.data.success) {
        setCounts({ chat: res.data.data.unreadCount });
      }
    } catch (error) {
      console.error('알림 카운트 조회 실패:', error);
    }
  }, []);

  useEffect(() => {
    fetchUnreadCount();
    const interval = setInterval(fetchUnreadCount, 10000);
    return () => clearInterval(interval);
  }, [fetchUnreadCount]);

  // 💡 refetch를 외부로 노출하여 특정 이벤트(읽음 처리 등) 발생 시 즉시 호출
  return { counts, refetch: fetchUnreadCount };
};
