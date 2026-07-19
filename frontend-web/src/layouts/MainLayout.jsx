import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { Outlet, Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import Sidebar from './Sidebar';
import TopNavbar from './TopNavbar';
import { approvalApi } from '../api/owner/ApprovalApi';
import { storeApi } from '../api/owner/storeApi'; // 대시보드 API 임포트

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
  const [hasChatRoom, setHasChatRoom] = useState(false); // 채팅방 개설 여부 상태 추가
  const [statusLoading, setStatusLoading] = useState(true);

  useEffect(() => {
    if (!accessToken) return;

    const role = localStorage.getItem('role');

    const fetchInitialData = async () => {
      try {
        if (role === 'ROLE_ADMIN') {
          const approvalRes = await approvalApi.getOwnerStoreChecklist();
          if (approvalRes.data && approvalRes.data.success) {
            setApprovalStatus(approvalRes.data.data.approvalStatus);
          }
          setStatusLoading(false);
          return;
        }

        const [approvalRes, dashboardRes] = await Promise.all([
          approvalApi.getOwnerStoreChecklist(),
          storeApi.getDashboard(),
        ]);
        if (approvalRes.data && approvalRes.data.success) {
          setApprovalStatus(approvalRes.data.data.approvalStatus);
        }

        if (dashboardRes && dashboardRes.success) {
          console.log('대시보드 정보:', dashboardRes.data);
          const serverData = dashboardRes.data;

          // 1. 방금 개설되어 로컬에 true 흔적이 있거나 백엔드가 true를 주면 존재(true)로 판정
          const isCreatedInLocal = localStorage.getItem('storeChatRoomCreated');
          const chatCreated =
            isCreatedInLocal || serverData.storeChatRoomCreated;

          // 2. ★ 중요: 이 상태를 React State에 집어넣어야 Sidebar가 즉시 읽어서 그립니다.
          setHasChatRoom(chatCreated);

          // 3. 로컬스토리지 동기화 (문자열 형태로 변환)
          localStorage.setItem('storeChatRoomCreated', String(chatCreated));

          // 4. 나중에 채팅 개설할 때 쓸 수 있게 storeId도 임시 저장
          if (serverData.storeId) {
            localStorage.setItem('my_store_id', String(serverData.storeId));
          }
        }
      } catch (error) {
        setApprovalStatus('REJECTED');
      } finally {
        setStatusLoading(false);
      }
    };

    fetchInitialData();
  }, [accessToken]);

  // 인증, 입점상태, 대시보드 정보가 다 올 때까지 안전하게 대기
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
    return <Navigate to="/login" replace />;
  }

  return (
    <LayoutWrapper>
      {/* 사이드바에 승인 상태와 함께 채팅방 개설 유무도 props로 주입 */}
      <Sidebar approvalStatus={approvalStatus} hasChatRoom={hasChatRoom} />

      <MainContent>
        <TopNavbar />
        <PageContainer>
          <Outlet context={{ approvalStatus, hasChatRoom }} />
        </PageContainer>
      </MainContent>
    </LayoutWrapper>
  );
}

export default MainLayout;
