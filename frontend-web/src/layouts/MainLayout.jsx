import styled from 'styled-components';
import { Outlet, Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
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
  const { accessToken, isLoading } = useAuth(); // 💡 인증 상태 꺼내기

  // 로딩 중 일때 사용자에게 안내 메시지 보여줌
  if (isLoading) {
    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          height: '100vh',
          fontSize: '16px',
          fontWeight: 'bold',
          color: '#009e60',
        }}
      >
        안전하게 세션을 연결하는 중입니다... 🔐
      </div>
    );
  }

  // 인증 토큰이 없으면 로그인 페이지로 리다이렉트
  if (!accessToken) {
    return <Navigate to="/login" replace />;
  }

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
