import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { Outlet, Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import Sidebar from './Sidebar';
import TopNavbar from './TopNavbar';
import { approvalApi } from '../api/owner/ApprovalApi';

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
  const { accessToken, isLoading: authLoading } = useAuth(); // 인증 상태 꺼내기

  // 사장님의 승인 상태를 관리할 전역/지역 상태 추가
  const [approvalStatus, setApprovalStatus] = useState(null);
  const [statusLoading, setStatusLoading] = useState(true);

  // 로그인된 유저(토큰이 있을 때)의 입점 심사 승인 상태를 가져오는 Effect
  useEffect(() => {
    if (!accessToken) return;

    const fetchApprovalStatus = async () => {
      try {
        const response = await approvalApi.getOwnerStoreChecklist();
        if (response.data.success) {
          // 백엔드 상태 동기화 ('PENDING', 'APPROVED', 'REJECTED')
          setApprovalStatus(response.data.data.approvalStatus);
        }
      } catch (error) {
        console.error('레이아웃 승인 상태 조회 실패:', error);
        setApprovalStatus('REJECTED'); // 에러 시 기본 방어 처리
      } finally {
        setStatusLoading(false);
      }
    };

    fetchApprovalStatus();
  }, [accessToken]);

  // 인증 세션 확인 중이거나 승인 상태 조회 중일 때 로딩 가드
  if (authLoading || (accessToken && statusLoading)) {
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
      {/* 왼쪽 사이드바에 현재 승인 상태를 넘겨주어 메뉴 비활성화 UI를 구현합니다. */}
      <Sidebar approvalStatus={approvalStatus} />

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
