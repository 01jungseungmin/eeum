import styled from 'styled-components';
import { Outlet, useNavigate } from 'react-router-dom';
import Sidebar from './Sidebar';
import TopNavbar from './TopNavbar';

const LayoutWrapper = styled.div`
  display: flex;
  height: 100vh;
  background-color: #f8f9fa;
  overflow-x: hidden;
  overflow-y: hidden;
`;

const MainContent = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
`;

const PageContainer = styled.div`
  padding: 40px;
  flex: 1;
  overflow-x: hidden;
`;

function MainLayout() {
  return (
    <LayoutWrapper>
      {/* 왼쪽 사이드바 (메뉴바) */}
      <Sidebar />
      <MainContent>
        {/* 상단 네비바 */}
        <TopNavbar />
        {/* 우측 하단 내용 변경 영역 */}
        <PageContainer>
          <Outlet />
        </PageContainer>
      </MainContent>
    </LayoutWrapper>
  );
}

export default MainLayout;
