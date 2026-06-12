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

        <Route path="/admin/dashboard" element={<AdminDashboardPage />} />
        <Route path="/admin/approval" element={<ApprovalPage />} />
        <Route path="/admin/approval/:id" element={<ApprovalDetailPage />} />
        <Route path="/admin/members" element={<MemberPage />} />

        {/* 나중에 추가될 다른 메뉴들 */}
        <Route path="/approval-status" element={<ApprovalStatus />} />
        <Route path="/dashboard" element={<OwnerDashboardPage />} />
        <Route path="/store" element={<StorePage />} />
        <Route
          path="/products"
          element={<div>상품 관리 페이지 (준비중)</div>}
        />
        <Route path="/order-management" element={<OrderManagementPage />} />
        <Route path="/reservation" element={<ReservationPage />} />
        <Route path="/categories" element={<CategoryPage />} />
        <Route path="/events" element={<EventPage />} />
        <Route path="/products" element={<ProductManagementPage />} />
      </Route>
    </Routes>
  );
}

export default App;
