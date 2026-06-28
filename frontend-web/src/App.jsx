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

// 💡 나중에 기능 제한할 때 주석 해제하세요!
import ApprovalGuard from './components/owner/ApprovalGuard';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/sign-up" element={<SignUpPage />} />
      <Route path="/find-password" element={<FindPasswordPage />} />
      <Route path="/admin/login" element={<AdminLoginPage />} />

      <Route element={<MainLayout />}>
        {/* 로그인 시 처음에 갈 곳 */}
        <Route path="/" element={<Navigate to="/approval-status" replace />} />

        {/* [어드민 메뉴] */}
        <Route path="/admin/dashboard" element={<AdminDashboardPage />} />
        <Route path="/admin/approval" element={<ApprovalPage />} />
        <Route path="/admin/approval/:id" element={<ApprovalDetailPage />} />
        <Route path="/admin/members" element={<MemberPage />} />

        {/* [사장님 - 승인 상태 페이지] */}
        <Route path="/approval-status" element={<ApprovalStatus />} />

        {/* 🔒 [추후 개발 완료 후 적용할 부분]
          다른 기능 개발 및 API 연동을 편하게 하기 위해 임시로 가드를 주석 처리했습니다.
          나중에 접근을 제한하려면 아래 <Route element={<ApprovalGuard />}> 주석을 풀고 
          하위 사장님 메뉴들을 안으로 넣어주시면 됩니다.
        */}
        <Route element={<ApprovalGuard />}>
          <Route path="/dashboard" element={<OwnerDashboardPage />} />
          <Route path="/store" element={<StorePage />} />
          <Route path="/products" element={<ProductManagementPage />} />
          <Route path="/order-management" element={<OrderManagementPage />} />
          <Route path="/reservation" element={<ReservationPage />} />
          <Route path="/categories" element={<CategoryPage />} />
          <Route path="/events" element={<EventPage />} />
        </Route>
      </Route>
    </Routes>
  );
}

export default App;
