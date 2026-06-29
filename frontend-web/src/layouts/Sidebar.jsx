import React from 'react';
import styled from 'styled-components';
import { useNavigate, useLocation } from 'react-router-dom';
import { OWNER_MENU_CONFIG, ADMIN_MENU_CONFIG } from '../config/MenuConfig';
import { useAuth } from '../contexts/AuthContext';
import { authApi } from '../api/authApi';
import axios from 'axios';

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
  cursor: ${(props) =>
    props.$disabled
      ? 'not-allowed'
      : 'pointer'}; /* 💡 권한 제한 시 금지 커서 */
  font-size: 15px;
  margin-bottom: 4px;
  position: relative;
  transition: all 0.2s ease;

  /* 💡 접근 권한이 없는 메뉴는 흐리게 처리 (opacity) */
  opacity: ${(props) => (props.$disabled ? 0.35 : 1)};

  background-color: ${(props) =>
    props.$active ? (props.$isAdmin ? '#0f4229' : '#2d5a43') : 'transparent'};
  color: ${(props) => (props.$active ? '#fff' : '#adb5bd')};

  &:hover {
    /* 💡 권한이 없는 메뉴는 호버 효과 제거 */
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

function Sidebar({ approvalStatus }) {
  // MainLayout에서 던져준 승인 상태 props로 받기
  const navigate = useNavigate();
  const location = useLocation();
  const { logout } = useAuth();

  const role = localStorage.getItem('role');
  const isAdmin = role === 'ROLE_ADMIN';

  // 사장님이면서 최종 입점 승인이 'APPROVED' 상태가 아니라면 제한 대상자가 됩니다.
  const isOwnerRestricted = !isAdmin && approvalStatus !== 'APPROVED';

  const menuConfig = isAdmin ? ADMIN_MENU_CONFIG : OWNER_MENU_CONFIG;

  const counts = {
    orders: 3,
    reviews: 2,
    chat: 5,
    qna: 3,
    alerts: 8,
    adminApproval: 12,
    adminReports: 5,
  };

  const handleMenuClick = async (item, isItemDisabled) => {
    // 접근 차단 대상인 메뉴인 경우 라우팅 및 액션을 차단
    if (isItemDisabled) {
      alert('입점 심사 승인이 완료된 후 사용하실 수 있습니다. 📋');
      return;
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
        console.error(
          '로그아웃 API 호출 실패 (아마도 토큰 만료):',
          error.response?.status,
        );
      } finally {
        logout();
        console.log('로컬 상태 정리 완료, 이동 경로:', targetPath);
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

              // 사장님 차단 대상 기간이더라도, '승인 상태' 메뉴와 '로그아웃' 버튼은 언제나 작동 가능해야 함
              const isItemDisabled =
                isOwnerRestricted &&
                item.id !== 'approval' &&
                item.id !== 'logout';

              return (
                <MenuItem
                  key={item.id}
                  onClick={() => handleMenuClick(item, isItemDisabled)} // 비활성화 여부 핸들러로 전달
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
