import { useState, useEffect } from "react";
import styled from "styled-components";
import { notificationApi } from "../../../api/owner/notificationApi";
import NotificationList from "../../../components/owner/notification/NotificationList";
import NotificationWidget from "../../../components/owner/notification/NotificationWidget";

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
  const [selectedFilter, setSelectedFilter] = useState("ALL");
  const [isGlobalNotificationOn, setIsGlobalNotificationOn] = useState(true);
  const [loading, setLoading] = useState(false);
  const [totalElements, setTotalElements] = useState(0);

  // 알림 목록 조회 함수
  useEffect(() => {
    const fetchInitialNotifications = async () => {
      setLoading(true);
      try {
        const response = await notificationApi.getNotifications("ALL");
        const data = response.data?.data;
        if (data) {
          setNotifications(data.content || []);
          setTotalElements(data.totalElements || 0);
        }
      } catch (error) {
        console.error("알림 목록 조회 실패:", error);
      } finally {
        setLoading(false);
      }
    };

    fetchInitialNotifications();
  }, []);

  const unreadCount = notifications.filter((n) => !n.read).length;

  // 모두 읽음 처리
  const handleMarkAllAsRead = async () => {
    try {
      await notificationApi.readNotification();
      setNotifications((prev) => prev.map((item) => ({ ...item, read: true })));
    } catch (error) {
      console.error("전체 읽음 처리 실패:", error);
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
      console.error("알림 삭제 실패:", error);
    }
  };

  // 전체 알림 삭제 (알림함 비우기)
  const handleDeleteAllNotifications = async () => {
    if (!window.confirm("모든 알림을 삭제하시겠습니까?")) {
      return;
    }

    try {
      await notificationApi.deleteAllNotifications();
      // 삭제 성공 시 리스트 및 총 개수 초기화
      setNotifications([]);
      setTotalElements(0);
    } catch (error) {
      console.error("전체 알림 삭제 실패:", error);
    }
  };

  // 알림 클릭 시 읽음 처리
  const handleItemClick = async (item) => {
    // 안 읽은 알림에 대해서만
    if (!item.read) {
      try {
        await notificationApi.readSingleNotification(item.notificationId);
        setNotifications((prev) =>
          prev.map((n) =>
            n.notificationId === item.notificationId ? { ...n, read: true } : n,
          ),
        );
      } catch (error) {
        console.error("개별 읽음 처리 실패:", error);
      }
    }
  };

  const handleToggleGlobalNotification = () => {
    setIsGlobalNotificationOn((prev) => !prev);
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
          onDeleteAllNotifications={handleDeleteAllNotifications} // ✨ 함수 전달
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
