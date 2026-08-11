import { useState, useEffect } from 'react';
import styled from 'styled-components';
import { useNavigate, useLocation } from 'react-router-dom';
import { EventSourcePolyfill } from 'event-source-polyfill';
import { OWNER_MENU_CONFIG, ADMIN_MENU_CONFIG } from '../config/MenuConfig';
import { useAuth } from '../contexts/AuthContext';
import { authApi } from '../api/authApi';
import { notificationApi } from '../api/owner/notificationApi';

const SideContainer = styled.div`
  width: 260px;
  background-color: ${(props) => (props.$isAdmin ? '#005936' : '#1a392a')};
  color: white;
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding: 20px 0;
  font-family: 'Pretendard', sans-serif;
  transition: background-color 0.2s ease;
`;

const LogoSection = styled.div`
  padding: 0 25px 30px;
  h2 {
    color: #fff;
    margin: 0;
    font-size: 22px;
    font-weight: 800;
  }
  p {
    color: ${(props) => (props.$isAdmin ? '#a3ccbe' : '#81c784')};
    font-size: 12px;
    margin: 5px 0 0;
  }
`;

const MenuSection = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: 0 15px;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: ${(props) => (props.$isAdmin ? '#004027' : '#2d5a43')};
    border-radius: 10px;
  }
`;

const MenuGroupLabel = styled.div`
  font-size: 11px;
  color: #6d8a7a;
  margin: 25px 0 10px 10px;
  font-weight: bold;
`;

const MenuItem = styled.div`
  display: flex;
  align-items: center;
  padding: 12px 15px;
  border-radius: 12px;
  cursor: ${(props) => (props.$disabled ? 'not-allowed' : 'pointer')};
  font-size: 15px;
  margin-bottom: 4px;
  position: relative;
  transition: all 0.2s ease;
  opacity: ${(props) => (props.$disabled ? 0.35 : 1)};

  background-color: ${(props) =>
    props.$active ? (props.$isAdmin ? '#0f4229' : '#2d5a43') : 'transparent'};
  color: ${(props) => (props.$active ? '#fff' : '#adb5bd')};

  &:hover {
    background-color: ${(props) =>
      props.$disabled
        ? ''
        : props.$active
          ? ''
          : props.$isAdmin
            ? '#0a3621'
            : '#264d39'};
    color: ${(props) => (props.$disabled ? '#adb5bd' : '#fff')};
  }
`;

const IconWrapper = styled.span`
  margin-right: 12px;
  display: flex;
  align-items: center;
  font-size: 18px;
  opacity: ${(props) => (props.$active ? '1' : '0.7')};
`;

const Badge = styled.span`
  background-color: ${(props) => (props.$isAdmin ? '#f1b913' : '#ff4d4f')};
  color: ${(props) => (props.$isAdmin ? '#000' : '#fff')};
  font-size: 11px;
  font-weight: bold;
  padding: 2px 8px;
  border-radius: 10px;
  margin-left: auto;
`;

const StatusBadge = styled.span`
  background-color: #ff4d4f;
  color: white;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  margin-left: auto;
  font-weight: bold;
`;

const CATEGORY_MAP = {
  orders: 'ORDER',
  reservations: 'RESERVATION',
  reviews: 'REVIEW',
  chat: 'CHAT',
  qna: 'COMMUNITY', // QNA 메뉴의 카테고리가 COMMUNITY/QNA 중 백엔드 스펙에 맞게 지정
  system: 'SYSTEM',
};

function Sidebar({ approvalStatus }) {
  const navigate = useNavigate();
  const location = useLocation();
  const { logout } = useAuth();

  const role = localStorage.getItem('role');
  const isAdmin = role === 'ROLE_ADMIN';

  // 알림 수량 실시간 상태
  const [counts, setCounts] = useState({
    orders: 0,
    reservations: 0,
    reviews: 0,
    chat: 0,
    qna: 0,
    alerts: 0,
    system: 0,
  });

  const isOwnerRestricted = !isAdmin && approvalStatus !== 'APPROVED';
  const menuConfig = isAdmin ? ADMIN_MENU_CONFIG : OWNER_MENU_CONFIG;

  // SSE 실시간 연결
  useEffect(() => {
    const { url, token } = notificationApi.getSubscribeInfo();
    if (!token) return;

    const eventSource = new EventSourcePolyfill(url, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
      heartbeatTimeout: 300000,
    });

    eventSource.addEventListener('unread-count', (event) => {
      try {
        const parsedData = JSON.parse(event.data);
        const byCategory =
          parsedData.data?.byCategory || parsedData.byCategory || {};

        setCounts({
          orders: byCategory.ORDER || 0,
          reservations: byCategory.RESERVATION || 0,
          reviews: byCategory.REVIEW || 0,
          chat: byCategory.CHAT || 0,
          qna: byCategory.COMMUNITY || byCategory.QNA || 0,
          system: byCategory.SYSTEM || 0,
          alerts: parsedData.data?.unreadCount || parsedData.unreadCount || 0,
        });
      } catch (error) {
        console.error('SSE 데이터 파싱 실패:', error);
      }
    });

    eventSource.onopen = () => {
      console.log('SSE 연결 성공');
    };

    eventSource.onerror = (err) => {
      console.error('SSE 연결 에러:', err);
      eventSource.close();
    };

    return () => {
      eventSource.close();
    };
  }, []);

  // 메뉴 클릭 핸들러
  const handleMenuClick = async (e, item) => {
    if (item.path === '/chat') {
      const isCreated = localStorage.getItem('storeChatRoomCreated') === 'true';

      if (!isCreated) {
        e.preventDefault();
        alert(
          '💡 먼저 대표 실시간 채팅방을 개설하셔야 합니다.\n[문의 관리] 페이지로 이동합니다.',
        );
        navigate('/inquiry');
        return;
      }
    }

    // ✨ 알림 배지가 있는 메뉴 클릭 시 해당 카테고리만 읽음 처리
    if (item.countKey && counts[item.countKey] > 0) {
      const category = CATEGORY_MAP[item.countKey];

      // UI 즉시 반영 (해당 카테고리만 0으로 차감)
      setCounts((prev) => ({
        ...prev,
        [item.countKey]: 0,
      }));

      // 해당 카테고리만 백엔드 읽음 처리 요청
      if (category) {
        try {
          await notificationApi.readSingleNotification(category);
        } catch (error) {
          console.error(`${category} 카테고리 알림 읽음 처리 실패:`, error);
        }
      }
    }

    if (item.action === 'LOGOUT') {
      const refreshToken = localStorage.getItem('refreshToken');
      const currentRole = localStorage.getItem('role');
      const targetPath =
        currentRole === 'ROLE_ADMIN' ? '/admin/login' : '/login';

      try {
        if (refreshToken) {
          await authApi.logout(refreshToken);
        }
      } catch (error) {
        console.error('로그아웃 API 에러:', error.response?.status);
      } finally {
        logout();
        navigate(targetPath);
      }
    } else if (item.path && item.path !== '#') {
      navigate(item.path);
    }
  };

  return (
    <SideContainer $isAdmin={isAdmin}>
      <LogoSection $isAdmin={isAdmin}>
        <h2>{isAdmin ? '이웃' : '이음'}</h2>
        <p>{isAdmin ? 'Admin Dashboard' : '사장님 전용 관리 센터'}</p>
      </LogoSection>

      <MenuSection $isAdmin={isAdmin}>
        {menuConfig.map((group, index) => (
          <div key={group.group || index}>
            {group.group && <MenuGroupLabel>{group.group}</MenuGroupLabel>}

            {group.items.map((item) => {
              const isActive = location.pathname === item.path;
              const isItemDisabled =
                isOwnerRestricted &&
                item.id !== 'approval' &&
                item.id !== 'logout';

              return (
                <MenuItem
                  key={item.id}
                  onClick={(e) => handleMenuClick(e, item)}
                  $active={isActive}
                  $isAdmin={isAdmin}
                  $disabled={isItemDisabled}
                >
                  <IconWrapper $active={isActive}>{item.icon}</IconWrapper>
                  {item.name}
                  {item.countKey && counts[item.countKey] > 0 && (
                    <Badge $isAdmin={isAdmin}>{counts[item.countKey]}</Badge>
                  )}
                  {item.status && <StatusBadge>{item.status}</StatusBadge>}
                </MenuItem>
              );
            })}
          </div>
        ))}
      </MenuSection>
    </SideContainer>
  );
}

export default Sidebar;
