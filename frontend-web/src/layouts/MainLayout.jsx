import { useCallback, useEffect, useState } from 'react';
import styled from 'styled-components';
import { Outlet, Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { NotificationProvider } from '../contexts/NotificationContext';
import Sidebar from './Sidebar';
import TopNavbar from './TopNavbar';
import { approvalApi } from '../api/owner/approvalApi';
import { storeApi } from '../api/owner/storeApi';
import { getApprovalBadge } from '../utils/approvalBadge';

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
  const [reviewRequestedAt, setReviewRequestedAt] = useState(null);
  // 조회에 실패해 임시로 REJECTED를 넣은 경우에는 배지를 달지 않기 위한 플래그
  const [approvalSynced, setApprovalSynced] = useState(false);
  const [dashboardData, setDashboardData] = useState(null);
  const [statusLoading, setStatusLoading] = useState(true);

  // 심사 신청·사업자번호 수정 등으로 체크리스트가 바뀌면 사이드바 배지도 따라가도록 페이지가 호출한다
  const syncApproval = useCallback((checklist) => {
    setApprovalStatus(checklist.approvalStatus);
    setReviewRequestedAt(checklist.reviewRequestedAt ?? null);
    setApprovalSynced(true);
  }, []);

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
          syncApproval(approvalRes.data.data);
        }

        // 대시보드 정보 처리
        if (dashboardRes && dashboardRes.success) {
          const serverData = dashboardRes.data;
          setDashboardData(serverData);

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
  }, [accessToken, syncApproval]);

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
    <NotificationProvider>
      <LayoutWrapper>
        <Sidebar
          approvalStatus={approvalStatus}
          approvalBadge={
            approvalSynced
              ? getApprovalBadge(approvalStatus, reviewRequestedAt)
              : null
          }
        />

        <MainContent>
          <TopNavbar />
          <PageContainer>
            <Outlet
              context={{ approvalStatus, dashboardData, syncApproval }}
            />
          </PageContainer>
        </MainContent>
      </LayoutWrapper>
    </NotificationProvider>
  );
}

export default MainLayout;
