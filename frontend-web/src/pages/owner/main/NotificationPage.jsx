import { useState, useEffect } from 'react';
import styled from 'styled-components';
import { notificationApi } from '../../../api/owner/notificationApi';
import NotificationList from '../../../components/owner/notification/NotificationList';
import NotificationWidget from '../../../components/owner/notification/NotificationWidget';

const MainContentContainer = styled.main`
  flex: 1;
  background-color: #f9fafb;
  padding: 32px 40px;
  min-height: 100vh;
  box-sizing: border-box;
  width: 100%;
`;

const MainLayout = styled.div`
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 24px;
  width: 100%;

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
`;

const NotificationPage = () => {
  const [notifications, setNotifications] = useState([]);
  const [selectedFilter, setSelectedFilter] = useState('ALL');
  const [isGlobalNotificationOn, setIsGlobalNotificationOn] = useState(true);
  const [loading, setLoading] = useState(false);
  const [totalElements, setTotalElements] = useState(0);

  // 초기 데이터(알림 목록 + 알림 설정) 병렬 조회
  useEffect(() => {
    const fetchInitialData = async () => {
      setLoading(true);
      try {
        const [notifRes, settingsRes] = await Promise.all([
          notificationApi.getNotifications('ALL'),
          notificationApi.getNotificationSettings(),
        ]);

        // 알림 목록 데이터 반영
        const notifData = notifRes.data?.data;
        if (notifData) {
          setNotifications(notifData.content || []);
          setTotalElements(notifData.totalElements || 0);
        }

        // 전체 알림 설정(allEnabled) 반영
        const settingsData = settingsRes.data?.data;
        if (settingsData && typeof settingsData.allEnabled === 'boolean') {
          console.log(settingsData);
          setIsGlobalNotificationOn(settingsData.allEnabled);
        }
      } catch (error) {
        console.error('초기 데이터 로드 실패:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchInitialData();
  }, []);

  const unreadCount = notifications.filter((n) => !n.read).length;

  // 모두 읽음 처리
  const handleMarkAllAsRead = async () => {
    try {
      await notificationApi.readNotification();
      setNotifications((prev) => prev.map((item) => ({ ...item, read: true })));
    } catch (error) {
      console.error('전체 읽음 처리 실패:', error);
    }
  };

  // 개별 알림 삭제
  const handleDeleteNotification = async (notificationId) => {
    try {
      await notificationApi.deleteNotification(notificationId);
      setNotifications((prev) =>
        prev.filter((n) => n.notificationId !== notificationId),
      );
      setTotalElements((prev) => Math.max(0, prev - 1));
    } catch (error) {
      console.error('알림 삭제 실패:', error);
    }
  };

  // 전체 알림 삭제 (알림함 비우기)
  const handleDeleteAllNotifications = async () => {
    if (!window.confirm('모든 알림을 삭제하시겠습니까?')) {
      return;
    }

    try {
      await notificationApi.deleteAllNotifications();
      setNotifications([]);
      setTotalElements(0);
    } catch (error) {
      console.error('전체 알림 삭제 실패:', error);
    }
  };

  // 알림 클릭 시 읽음 처리
  const handleItemClick = async (item) => {
    if (!item.read) {
      try {
        await notificationApi.readSingleNotification(item.notificationId);
        setNotifications((prev) =>
          prev.map((n) =>
            n.notificationId === item.notificationId ? { ...n, read: true } : n,
          ),
        );
      } catch (error) {
        console.error('개별 읽음 처리 실패:', error);
      }
    }
  };

  // 전체 알림 토글 변경
  const handleToggleGlobalNotification = async () => {
    const nextState = !isGlobalNotificationOn;

    setIsGlobalNotificationOn(nextState);

    try {
      await notificationApi.updateNotificationSettings({
        allEnabled: nextState,
      });
    } catch (e) {
      console.error('전체 알림 설정 변경 실패:', e);
      // API 실패 시 이전 상태로 복구
      setIsGlobalNotificationOn(!nextState);
      alert('알림 설정 변경 중 오류가 발생했습니다.');
    }
  };

  return (
    <MainContentContainer>
      <MainLayout>
        <NotificationList
          notifications={notifications}
          selectedFilter={selectedFilter}
          onSelectFilter={setSelectedFilter}
          onMarkAllAsRead={handleMarkAllAsRead}
          onDeleteNotification={handleDeleteNotification}
          onDeleteAllNotifications={handleDeleteAllNotifications}
          onItemClick={handleItemClick}
          unreadCount={unreadCount}
          totalElements={totalElements}
          loading={loading}
        />

        <NotificationWidget
          isGlobalNotificationOn={isGlobalNotificationOn}
          onToggleGlobalNotification={handleToggleGlobalNotification}
          unreadCount={unreadCount}
          totalCount={totalElements}
        />
      </MainLayout>
    </MainContentContainer>
  );
};

export default NotificationPage;
