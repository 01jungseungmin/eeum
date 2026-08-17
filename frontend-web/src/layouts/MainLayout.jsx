import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { Outlet, Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import Sidebar from './Sidebar';
import TopNavbar from './TopNavbar';
import { approvalApi } from '../api/owner/ApprovalApi';
import { storeApi } from '../api/owner/storeApi';

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
  const { accessToken, isLoading: authLoading } = useAuth();

  const [approvalStatus, setApprovalStatus] = useState(null);
  const [statusLoading, setStatusLoading] = useState(true);

  useEffect(() => {
    if (!accessToken) return;

    const role = sessionStorage.getItem('role');

    // 초기 데이터 로드 함수
    const fetchInitialData = async () => {
      try {
        if (role === 'ROLE_ADMIN') return;

        // 사장님 권한일 경우, 승인 상태와 대시보드 정보를 동시에 가져오기
        const [approvalRes, dashboardRes] = await Promise.all([
          approvalApi.getOwnerStoreChecklist(),
          storeApi.getDashboard(),
        ]);
        if (approvalRes.data && approvalRes.data.success) {
          setApprovalStatus(approvalRes.data.data.approvalStatus);
        }

        // 대시보드 정보 처리
        if (dashboardRes && dashboardRes.success) {
          const serverData = dashboardRes.data;

          // 방금 개설되어 로컬에 true 흔적이 있거나 백엔드가 true를 주면 존재(true)로 판정
          const isCreatedInLocal =
            sessionStorage.getItem('storeChatRoomCreated') === 'true';
          const chatCreated =
            isCreatedInLocal || serverData.storeChatRoomCreated;

          // 개설 여부 상태 동기화
          sessionStorage.setItem(
            'storeChatRoomCreated',
            String(Boolean(chatCreated)),
          );

          // storeChatRoomId가 유효할 때만 로컬스토리지 업데이트
          const targetRoomId = serverData.storeChatRoomId;
          if (
            targetRoomId !== undefined &&
            targetRoomId !== null &&
            String(targetRoomId) !== 'undefined'
          ) {
            sessionStorage.setItem('storeChatRoom_id', String(targetRoomId));
          }

          // storeId 저장
          if (serverData.storeId) {
            sessionStorage.setItem('my_store_id', String(serverData.storeId));
          }
        }
      } catch (error) {
        console.error('초기 데이터 로드 실패:', error);
        setApprovalStatus('REJECTED');
      } finally {
        setStatusLoading(false);
      }
    };

    fetchInitialData();
  }, [accessToken]);

  // 인증 및 승인 상태 데이터 로딩 대기
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

  if (!accessToken) {
    return (
      <Navigate
        to="/login"
        replace
      />
    );
  }

  return (
    <LayoutWrapper>
      <Sidebar approvalStatus={approvalStatus} />

      <MainContent>
        <TopNavbar />
        <PageContainer>
          <Outlet context={{ approvalStatus }} />
        </PageContainer>
      </MainContent>
    </LayoutWrapper>
  );
}

export default MainLayout;
