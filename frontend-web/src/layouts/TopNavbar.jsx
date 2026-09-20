import { useEffect, useMemo, useRef, useState } from 'react';
import styled from 'styled-components';
import { useLocation, useNavigate } from 'react-router-dom';
import { Bell, CheckCheck } from 'lucide-react';
import { findMenuByPath } from '../config/MenuConfig';
import { useNotification } from '../contexts/NotificationContext';
import { accountApi } from '../api/owner/accountApi';
import { notificationApi } from '../api/owner/notificationApi';
import { formatRelativeTime } from '../utils/relativeTime';

const NavContainer = styled.div`
  height: 80px;
  background: white;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 30px;
  border-bottom: 1px solid #f0f0f0;
`;

const TitleSection = styled.div`
  h2 {
    margin: 0;
    font-size: 20px;
    font-weight: bold;
    color: #1a392a;
  }
  p {
    margin: 4px 0 0;
    font-size: 13px;
    color: #999;
  }
`;

const RightSection = styled.div`
  display: flex;
  align-items: center;
  gap: 20px;
`;

const BellWrapper = styled.div`
  position: relative;
`;

const BellButton = styled.button`
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
  border: none;
  border-radius: 50%;
  background: ${(props) => (props.$open ? '#f1f3f5' : 'transparent')};
  cursor: pointer;

  &:hover {
    background: #f1f3f5;
  }

  .badge {
    position: absolute;
    top: -3px;
    right: -5px;
    min-width: 18px;
    background: #ff4d4f;
    color: white;
    font-size: 10px;
    font-weight: 700;
    line-height: 14px;
    padding: 1px 5px;
    border-radius: 10px;
    border: 2px solid white;
    box-sizing: border-box;
  }
`;

const Dropdown = styled.div`
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 340px;
  background: white;
  border: 1px solid #eee;
  border-radius: 14px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 100;
  overflow: hidden;
`;

const DropdownHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #f3f3f3;

  h4 {
    margin: 0;
    font-size: 14px;
    font-weight: 700;
    color: #262626;
  }

  button {
    display: flex;
    align-items: center;
    gap: 4px;
    border: none;
    background: none;
    color: #52a06f;
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;

    &:disabled {
      color: #bfbfbf;
      cursor: default;
    }
  }
`;

const NotificationItem = styled.div`
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
  background: ${(props) => (props.$unread ? '#f6fbf8' : 'white')};

  &:hover {
    background: #f5f5f5;
  }

  .dot {
    flex-shrink: 0;
    width: 7px;
    height: 7px;
    margin-top: 6px;
    border-radius: 50%;
    background: ${(props) => (props.$unread ? '#ff4d4f' : 'transparent')};
  }

  .body {
    min-width: 0;
    flex: 1;
  }

  .title {
    font-size: 13px;
    font-weight: ${(props) => (props.$unread ? 700 : 500)};
    color: #262626;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .content {
    margin-top: 2px;
    font-size: 12px;
    color: #8c8c8c;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .time {
    flex-shrink: 0;
    font-size: 11px;
    color: #bfbfbf;
  }
`;

const DropdownMessage = styled.div`
  padding: 32px 16px;
  text-align: center;
  color: #bfbfbf;
  font-size: 13px;
`;

const DropdownFooter = styled.button`
  width: 100%;
  padding: 12px;
  border: none;
  border-top: 1px solid #f3f3f3;
  background: white;
  color: #52a06f;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    background: #f9f9f9;
  }
`;

const ProfileBox = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 14px 6px 12px;
  border: 1px solid #eee;
  border-radius: 30px;
  .avatar {
    width: 32px;
    height: 32px;
    background: #4caf50;
    color: white;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: bold;
    overflow: hidden;
    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }
  .info {
    text-align: left;
    .name {
      font-size: 14px;
      font-weight: bold;
    }
    .role {
      font-size: 11px;
      color: #999;
    }
  }
`;

// 역할별 헤더 표시 문구 (ROLE_USER는 입점 심사 전인 사장 회원)
const ROLE_LABEL = {
  ROLE_ADMIN: '플랫폼 관리자',
  ROLE_OWNER: '사장님',
  ROLE_USER: '사장 회원',
};

const RECENT_NOTIFICATION_SIZE = 5;

function TopNavbar() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { counts, setCounts } = useNotification();

  const userRole = useMemo(() => {
    return sessionStorage.getItem('role') || 'ROLE_OWNER';
  }, []);
  const isAdmin = userRole === 'ROLE_ADMIN';

  const [profile, setProfile] = useState(null);
  const [isOpen, setIsOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState(false);
  const bellRef = useRef(null);

  // 내 프로필(닉네임/프로필 이미지) 조회
  useEffect(() => {
    let isMounted = true;

    const fetchProfile = async () => {
      try {
        const response = await accountApi.getMyInfo();
        if (isMounted && response.data?.success) {
          setProfile(response.data.data);
        }
      } catch (error) {
        console.error('내 정보 조회 실패:', error);
      }
    };

    fetchProfile();

    return () => {
      isMounted = false;
    };
  }, []);

  // 드롭다운 바깥 클릭 / ESC 로 닫기
  useEffect(() => {
    if (!isOpen) return;

    const handleClickOutside = (event) => {
      if (bellRef.current && !bellRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    };
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') setIsOpen(false);
    };

    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  // 유저 권한과 라우터 경로를 기반으로 매칭되는 메뉴 객체 색인
  const currentMenu = useMemo(() => {
    return findMenuByPath(pathname, userRole);
  }, [pathname, userRole]);

  // 실시간 요일 포맷팅
  const today = useMemo(() => {
    return new Intl.DateTimeFormat('ko-KR', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      weekday: 'long',
    }).format(new Date());
  }, []);

  const menuName = currentMenu?.name || '상세 정보';
  const menuSubtitle = currentMenu?.subtitle || '상세 내역을 확인합니다.';

  const fetchRecentNotifications = async () => {
    setListLoading(true);
    setListError(false);
    try {
      const response = await notificationApi.getNotifications(
        'ALL',
        0,
        RECENT_NOTIFICATION_SIZE,
      );
      setNotifications(response.data?.data?.content || []);
    } catch (error) {
      console.error('최근 알림 조회 실패:', error);
      setListError(true);
    } finally {
      setListLoading(false);
    }
  };

  const handleBellClick = () => {
    const nextOpen = !isOpen;
    setIsOpen(nextOpen);
    if (nextOpen) fetchRecentNotifications();
  };

  // 알림 하나를 읽음 처리 (정확한 카테고리별 개수는 서버가 SSE로 다시 내려준다)
  const handleItemClick = async (notification) => {
    if (notification.read) return;

    setNotifications((prev) =>
      prev.map((item) =>
        item.notificationId === notification.notificationId
          ? { ...item, read: true }
          : item,
      ),
    );
    setCounts((prev) => ({ ...prev, alerts: Math.max(0, prev.alerts - 1) }));

    try {
      await notificationApi.readSingleNotification(notification.notificationId);
    } catch (error) {
      console.error('알림 읽음 처리 실패:', error);
      fetchRecentNotifications();
    }
  };

  const handleReadAll = async () => {
    try {
      await notificationApi.readNotification();
      setNotifications((prev) => prev.map((item) => ({ ...item, read: true })));
      setCounts((prev) => ({
        ...prev,
        orders: 0,
        reservations: 0,
        reviews: 0,
        chat: 0,
        qna: 0,
        system: 0,
        alerts: 0,
      }));
    } catch (error) {
      console.error('전체 읽음 처리 실패:', error);
    }
  };

  const displayName = profile?.nickname || profile?.name || '';
  const unreadCount = counts.alerts;

  return (
    <NavContainer>
      <TitleSection>
        <h2>{menuName}</h2>
        <p>
          {today} · {menuSubtitle}
        </p>
      </TitleSection>

      <RightSection>
        <BellWrapper ref={bellRef}>
          <BellButton
            type="button"
            aria-label="알림"
            $open={isOpen}
            onClick={handleBellClick}
          >
            <Bell
              size={22}
              color="#666"
            />
            {unreadCount > 0 && (
              <span className="badge">
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}
          </BellButton>

          {isOpen && (
            <Dropdown>
              <DropdownHeader>
                <h4>알림</h4>
                <button
                  type="button"
                  onClick={handleReadAll}
                  disabled={unreadCount === 0}
                >
                  <CheckCheck size={14} /> 모두 읽음
                </button>
              </DropdownHeader>

              {listLoading ? (
                <DropdownMessage>불러오는 중...</DropdownMessage>
              ) : listError ? (
                <DropdownMessage>알림을 불러오지 못했어요.</DropdownMessage>
              ) : notifications.length === 0 ? (
                <DropdownMessage>새로운 알림이 없어요.</DropdownMessage>
              ) : (
                notifications.map((notification) => (
                  <NotificationItem
                    key={notification.notificationId}
                    $unread={!notification.read}
                    onClick={() => handleItemClick(notification)}
                  >
                    <span className="dot" />
                    <div className="body">
                      <div className="title">{notification.title}</div>
                      <div className="content">{notification.content}</div>
                    </div>
                    <span className="time">
                      {formatRelativeTime(notification.createdAt)}
                    </span>
                  </NotificationItem>
                ))
              )}

              {/* 관리자에게는 알림 목록 페이지가 없어 사장 화면에서만 노출 */}
              {!isAdmin && (
                <DropdownFooter
                  type="button"
                  onClick={() => {
                    setIsOpen(false);
                    navigate('/notifications');
                  }}
                >
                  알림 전체 보기
                </DropdownFooter>
              )}
            </Dropdown>
          )}
        </BellWrapper>

        <ProfileBox>
          <div className="avatar">
            {profile?.profileImageUrl ? (
              <img
                src={profile.profileImageUrl}
                alt={displayName}
              />
            ) : (
              displayName.charAt(0) || ROLE_LABEL[userRole]?.charAt(0)
            )}
          </div>
          <div className="info">
            <div className="name">
              {displayName || ROLE_LABEL[userRole] || ''}
            </div>
            <div className="role">{ROLE_LABEL[userRole] || ''}</div>
          </div>
        </ProfileBox>
      </RightSection>
    </NavContainer>
  );
}

export default TopNavbar;
