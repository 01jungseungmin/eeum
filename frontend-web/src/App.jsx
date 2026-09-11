import { useEffect, useState } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/owner/login/LoginPage';
import SignUpPage from './pages/owner/login/SignUpPage';
import FindPasswordPage from './pages/owner/login/FindPasswordPage';
import MainLayout from './layouts/MainLayout';
import ApprovalStatus from './pages/owner/main/ApprovalStatusPage';
import OwnerDashboardPage from './pages/owner/main/DashBoardPage';
import AdminLoginPage from './pages/admin/login/AdminLoginPage';
import AdminDashboardPage from './pages/admin/main/DashBoardPage';
import ApprovalPage from './pages/admin/main/ApprovalPage';
import ApprovalDetailPage from './pages/admin/main/ApprovalDetailPage';
import MemberPage from './pages/admin/main/MemberPage';
import StorePage from './pages/owner/main/StorePage';
import CategoryPage from './pages/owner/main/CategoryPage';
import EventPage from './pages/owner/main/EventPage';
import OrderManagementPage from './pages/owner/main/OrderManagementPage';
import ReservationPage from './pages/owner/main/ReservationPage';
import ProductManagementPage from './pages/owner/main/ProductManagementPage';
import ReviewManagementPage from './pages/owner/main/ReviewManagementPage';
import InquiryManagementPage from './pages/owner/main/InquiryManagementPage';
import ChatManagementPage from './pages/owner/main/ChatManagementPage';
import CustomerManagementPage from './pages/owner/main/CustomerManagementPage';
import OwnerReportManagementPage from './pages/owner/main/ReportManagementPage';
import AiManagerPage from './pages/owner/main/AiManagerPage';
import AiChatPage from './pages/owner/main/AiChatPage';
import AiCareDetailPage from './pages/owner/main/AiCareDetailPage';
import AiPlanManagementPage from './pages/owner/main/AiPlanManagementPage';
import AiNoticeCreatePage from './pages/owner/main/AiNoticeCreatePage';
import AiReviewDetailNoticePage from './pages/owner/main/AiReviewDetailNoticePage';
import AiEventDetailPage from './pages/owner/main/AiEventDetailPage';
import AiCreateEventPage from './pages/owner/main/AiCreateEventPage';
import AiLocationMatchingDetailPage from './pages/owner/main/AiLocationMatchingDetailPage';
import AiMarketingPage from './pages/owner/main/AiMarketingPage';
import AiOperationRiskDetailPage from './pages/owner/main/AiOperationRiskDetailPage';
import AiSavingPlanCreatePage from './pages/owner/main/AiSavingPlanCreatePage';
import AiPowerUsageReportPage from './pages/owner/main/AiPowerUsageReportPage';
import AdminReportManagementPage from './pages/admin/main/ReportManagementPage';
import AdminReportDetailPage from './pages/admin/main/ReportDetailPage';
import NotificationPage from './pages/owner/main/NotificationPage';
import AdminInquiryManagementPage from './pages/admin/main/InquiryManagementPage';
import AdminInquiryDetailPage from './pages/admin/main/InquiryDetailPage';
import AdminCategoryManagementPage from './pages/admin/main/CategoryManagementPage';
import AdminPostManagementPage from './pages/admin/main/PostManagementPage';
import AdminPostDetailPage from './pages/admin/main/PostDetailPage';

import ApprovalGuard from './components/owner/ApprovalGuard';
import { approvalApi } from './api/owner/ApprovalApi';

// 루트 경로("/")에서 유저 상태에 맞춰 대시보드 또는 심사창으로 스위칭해주는 지능형 컴포넌트
function InitialRedirect() {
  const [targetPath, setTargetPath] = useState(null);
  const role = sessionStorage.getItem('role');

  useEffect(() => {
    if (role === 'ROLE_ADMIN') {
      setTargetPath('/admin/dashboard');
      return;
    }

    const checkApproval = async () => {
      try {
        const response = await approvalApi.getOwnerStoreChecklist();
        if (
          response.data.success &&
          response.data.data.approvalStatus === 'APPROVED'
        ) {
          setTargetPath('/dashboard');
        } else {
          setTargetPath('/approval-status');
        }
      } catch (error) {
        console.error('Error fetching approval status:', error);
        setTargetPath('/approval-status');
      }
    };

    checkApproval();
  }, [role]);

  if (!targetPath) return null; // 불필요한 로딩 메시지 깜빡임 제거

  return (
    <Navigate
      to={targetPath}
      replace
    />
  );
}

function App() {
  return (
    <Routes>
      <Route
        path="/login"
        element={<LoginPage />}
      />
      <Route
        path="/sign-up"
        element={<SignUpPage />}
      />
      <Route
        path="/find-password"
        element={<FindPasswordPage />}
      />
      <Route
        path="/admin/login"
        element={<AdminLoginPage />}
      />

      <Route element={<MainLayout />}>
        {/* 로그인 후 최초 메인 주소 진입 시 승인 여부에 따라 동적 이동 분기 처리 */}
        <Route
          path="/"
          element={<InitialRedirect />}
        />

        {/* 어드민 메뉴 */}
        <Route
          path="/admin/dashboard"
          element={<AdminDashboardPage />}
        />
        <Route
          path="/admin/approval"
          element={<ApprovalPage />}
        />
        <Route
          path="/admin/approval/:id"
          element={<ApprovalDetailPage />}
        />
        <Route
          path="/admin/members"
          element={<MemberPage />}
        />
        <Route
          path="/admin/reports"
          element={<AdminReportManagementPage />}
        />
        <Route
          path="/admin/reports/:id"
          element={<AdminReportDetailPage />}
        />
        <Route
          path="/admin/inquiry"
          element={<AdminInquiryManagementPage />}
        />
        <Route
          path="/admin/inquiry/:id"
          element={<AdminInquiryDetailPage />}
        />
        <Route
          path="/admin/categories"
          element={<AdminCategoryManagementPage />}
        />
        <Route
          path="/admin/posts"
          element={<AdminPostManagementPage />}
        />
        <Route
          path="/admin/posts/:id"
          element={<AdminPostDetailPage />}
        />

        {/* 심사 중에도 접근 허용을 위해 보호막 외부에 배치 */}
        <Route
          path="/approval-status"
          element={<ApprovalStatus />}
        />

        {/* 최종 입점 승인(APPROVED)이 완료된 사장님만 탐색 허용 */}
        <Route element={<ApprovalGuard />}>
          <Route
            path="/dashboard"
            element={<OwnerDashboardPage />}
          />
          <Route
            path="/store"
            element={<StorePage />}
          />
          <Route
            path="/products"
            element={<ProductManagementPage />}
          />
          <Route
            path="/order-management"
            element={<OrderManagementPage />}
          />
          <Route
            path="/reservation"
            element={<ReservationPage />}
          />
          <Route
            path="/categories"
            element={<CategoryPage />}
          />
          <Route
            path="/events"
            element={<EventPage />}
          />
          <Route
            path="/reviews"
            element={<ReviewManagementPage />}
          />
          <Route
            path="/inquiry"
            element={<InquiryManagementPage />}
          />
          <Route
            path="/chat"
            element={<ChatManagementPage />}
          />
          <Route
            path="/customers"
            element={<CustomerManagementPage />}
          />
          <Route
            path="/reports"
            element={<OwnerReportManagementPage />}
          />
          <Route
            path="/notifications"
            element={<NotificationPage />}
          />
          <Route
            path="/ai-manager"
            element={<AiManagerPage />}
          />
          <Route
            path="/ai-manager/chat"
            element={<AiChatPage />}
          />
          <Route
            path="/ai-manager/care"
            element={<AiCareDetailPage />}
          />
          <Route
            path="/ai-manager/notice"
            element={<AiNoticeCreatePage />}
          />
          <Route
            path="/ai-manager/review"
            element={<AiReviewDetailNoticePage />}
          />
          <Route
            path="/ai-manager/event"
            element={<AiEventDetailPage />}
          />
          <Route
            path="/ai-manager/event/create"
            element={<AiCreateEventPage />}
          />
          <Route
            path="/ai-manager/location"
            element={<AiLocationMatchingDetailPage />}
          />
          <Route
            path="/ai-manager/marketing"
            element={<AiMarketingPage />}
          />
          <Route
            path="/ai-manager/operation-risk"
            element={<AiOperationRiskDetailPage />}
          />
          <Route
            path="/ai-manager/saving-plan"
            element={<AiSavingPlanCreatePage />}
          />
          <Route
            path="/ai-manager/power-report"
            element={<AiPowerUsageReportPage />}
          />
          <Route
            path="/ai-manager/plan"
            element={<AiPlanManagementPage />}
          />
        </Route>
      </Route>
    </Routes>
  );
}

export default App;
