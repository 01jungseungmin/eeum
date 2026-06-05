import styled from 'styled-components';
import { useNavigate, useLocation } from 'react-router-dom';
import { OWNER_MENU_CONFIG, ADMIN_MENU_CONFIG } from '../config/MenuConfig';
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
  cursor: pointer;
  font-size: 15px;
  margin-bottom: 4px;
  position: relative;
  transition: all 0.2s ease;

  background-color: ${(props) =>
    props.$active ? (props.$isAdmin ? '#0f4229' : '#2d5a43') : 'transparent'};
  color: ${(props) => (props.$active ? '#fff' : '#adb5bd')};

  &:hover {
    background-color: ${(props) =>
      props.$active ? '' : props.$isAdmin ? '#0a3621' : '#264d39'};
    color: #fff;
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

function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();

  // 로컬스토리지에 있는 role에 따라 관리자용 메뉴, 사장님용 메뉴를 구분해서 보여줌
  const role = localStorage.getItem('role');
  const isAdmin = role === 'ROLE_ADMIN';

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

  const handleMenuClick = async (item) => {
    if (item.action === 'LOGOUT') {
      const accessToken = localStorage.getItem('accessToken');
      const refreshToken = localStorage.getItem('refreshToken');
      const currentRole = localStorage.getItem('role');

      const targetPath =
        currentRole === 'ROLE_ADMIN' ? '/admin/login' : '/login';

      try {
        if (accessToken && refreshToken) {
          await axios.post(
            'http://localhost:8080/auth/logout',
            { refreshToken: refreshToken },
            {
              headers: {
                Authorization: `Bearer ${accessToken}`,
              },
            },
          );
        }
      } catch (error) {
        console.error(
          '로그아웃 API 호출 실패 (아마도 토큰 만료):',
          error.response?.status,
        );
      } finally {
        // 성공하든 실패하든(401 등) 로컬 스토리지는 비우고 페이지를 이동
        localStorage.removeItem('role');
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');

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
              return (
                <MenuItem
                  key={item.id}
                  onClick={() => handleMenuClick(item)}
                  $active={isActive}
                  $isAdmin={isAdmin}
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
