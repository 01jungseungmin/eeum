import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from 'react';
import { useLocation } from 'react-router-dom';
import { EventSourcePolyfill } from 'event-source-polyfill';
import { useAuth } from './AuthContext';
import { notificationApi } from '../api/owner/notificationApi';
import { dashboardApi } from '../api/admin/dashboardApi';

const NotificationContext = createContext();

const INITIAL_COUNTS = {
  orders: 0,
  reservations: 0,
  reviews: 0,
  chat: 0,
  qna: 0,
  system: 0,
  // 전체 안 읽은 알림 수 (헤더 종 아이콘)
  alerts: 0,
  // 관리자 사이드바 배지: 처리 대기 중인 사장 승인 / 신고 건수
  adminApproval: 0,
  adminReports: 0,
};

const RECONNECT_BASE_MS = 3000;
const RECONNECT_MAX_MS = 30000;
const ADMIN_PENDING_REFRESH_MS = 60 * 1000;

// 사이드바와 헤더가 같은 알림 개수를 보도록 SSE 구독을 한 곳에서만 연다.
export const NotificationProvider = ({ children }) => {
  const { accessToken } = useAuth();
  const { pathname } = useLocation();
  const [counts, setCounts] = useState(INITIAL_COUNTS);

  const isAdmin = sessionStorage.getItem('role') === 'ROLE_ADMIN';

  // SSE 실시간 연결 (accessToken이 재발급되어 바뀔 때마다 새 토큰으로 재연결)
  useEffect(() => {
    if (!accessToken) return;

    const { url } = notificationApi.getSubscribeInfo();
    let eventSource = null;
    let retryTimer = null;
    let retryCount = 0;
    let isClosed = false;

    const connect = () => {
      eventSource = new EventSourcePolyfill(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        heartbeatTimeout: 300000,
      });

      eventSource.addEventListener('unread-count', (event) => {
        try {
          const parsedData = JSON.parse(event.data);
          const byCategory =
            parsedData.data?.byCategory || parsedData.byCategory || {};

          setCounts((prev) => ({
            ...prev,
            orders: byCategory.ORDER || 0,
            reservations: byCategory.RESERVATION || 0,
            reviews: byCategory.REVIEW || 0,
            chat: byCategory.CHAT || 0,
            qna: byCategory.COMMUNITY || byCategory.QNA || 0,
            system: byCategory.SYSTEM || 0,
            alerts: parsedData.data?.unreadCount || parsedData.unreadCount || 0,
          }));
        } catch (error) {
          console.error('SSE 데이터 파싱 실패:', error);
        }
      });

      eventSource.onopen = () => {
        retryCount = 0;
      };

      // 연결이 끊기면 알림 개수가 멈춰 버리므로, 점점 간격을 늘리며 다시 연결한다
      eventSource.onerror = (err) => {
        console.error('SSE 연결 에러:', err);
        eventSource.close();

        if (isClosed) return;
        const delay = Math.min(
          RECONNECT_BASE_MS * 2 ** retryCount,
          RECONNECT_MAX_MS,
        );
        retryCount += 1;
        retryTimer = setTimeout(connect, delay);
      };
    };

    connect();

    return () => {
      isClosed = true;
      clearTimeout(retryTimer);
      eventSource?.close();
    };
  }, [accessToken]);

  // 관리자 사이드바 배지 — 승인 대기 / 미처리 신고 건수 (대시보드 요약과 같은 기준)
  const refreshAdminPending = useCallback(async () => {
    try {
      const response = await dashboardApi.getSummary();
      if (response.data?.success) {
        const { pendingOwnerApprovals, pendingReports } = response.data.data;
        setCounts((prev) => ({
          ...prev,
          adminApproval: pendingOwnerApprovals || 0,
          adminReports: pendingReports || 0,
        }));
      }
    } catch (error) {
      console.error('관리자 대기 건수 조회 실패:', error);
    }
  }, []);

  // 화면을 이동할 때(처리 후 돌아오는 경우 등)와 주기적으로 다시 조회한다
  useEffect(() => {
    if (!accessToken || !isAdmin) return;
    queueMicrotask(() => refreshAdminPending());
  }, [accessToken, isAdmin, pathname, refreshAdminPending]);

  useEffect(() => {
    if (!accessToken || !isAdmin) return;
    const timerId = setInterval(refreshAdminPending, ADMIN_PENDING_REFRESH_MS);
    return () => clearInterval(timerId);
  }, [accessToken, isAdmin, refreshAdminPending]);

  return (
    <NotificationContext.Provider value={{ counts, setCounts }}>
      {children}
    </NotificationContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useNotification = () => useContext(NotificationContext);
