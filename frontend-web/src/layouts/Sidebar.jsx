import styled from 'styled-components';
import { useNavigate, useLocation } from 'react-router-dom';
import { MENU_CONFIG } from '../config/MenuConfig';

const SideContainer = styled.div`
  width: 260px;
  background-color: #1a392a;
  color: white;
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding: 20px 0;
  font-family: 'Pretendard', sans-serif;
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
    color: #81c784;
    font-size: 12px;
    margin: 5px 0 0;
  }
`;

const MenuSection = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: 0 15px;

  /* 스크롤바 디자인 (필요시) */
  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: #2d5a43;
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

  /* 이미지처럼 선택된 메뉴 하이라이트 */
  background-color: ${(props) =>
    props.$active ? (props.$special ? '#4caf50' : '#2d5a43') : 'transparent'};
  color: ${(props) => (props.$active ? '#fff' : '#adb5bd')};

  &:hover {
    background-color: ${(props) => (props.$active ? '' : '#264d39')};
    color: #fff;
  }
`;

const IconWrapper = styled.span`
  margin-right: 12px;
  display: flex;
  align-items: center;
  font-size: 18px;
`;

const Badge = styled.span`
  background-color: #ff4d4f;
  color: white;
  font-size: 11px;
  font-weight: bold;
  padding: 2px 8px;
  border-radius: 10px;
  margin-left: auto; // 오른쪽 끝으로 밀기
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

  const counts = { orders: 3, reviews: 2, chat: 5, qna: 3, alerts: 8 };

  return (
    <SideContainer>
      <LogoSection>
        <h2>이음</h2>
        <p>사장님 전용 관리 센터</p>
      </LogoSection>
      <MenuSection>
        {MENU_CONFIG.map((group) => (
          <div key={group.group}>
            <MenuGroupLabel>{group.group}</MenuGroupLabel>
            {group.items.map((item) => (
              <MenuItem
                key={item.id}
                onClick={() => navigate(item.path)}
                $active={location.pathname === item.path}
                $special={item.isSpecial}
              >
                <IconWrapper>{item.icon}</IconWrapper>
                {item.name}
                {item.countKey && counts[item.countKey] > 0 && (
                  <Badge>{counts[item.countKey]}</Badge>
                )}
                {item.status && <StatusBadge>{item.status}</StatusBadge>}
              </MenuItem>
            ))}
          </div>
        ))}
      </MenuSection>
    </SideContainer>
  );
}

export default Sidebar;
