import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/login/LoginPage';
import SignUpPage from './pages/login/SignUpPage';
import FindPasswordPage from './pages/login/FindPasswordPage';
import MainLayout from './layouts/MainLayout';
import ApprovalStatus from './pages/main/ApprovalStatusPage';
import DashboardPage from './pages/main/DashBoardPage';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/sign-up" element={<SignUpPage />} />
      <Route path="/find-password" element={<FindPasswordPage />} />

      <Route element={<MainLayout />}>
        {/* 로그인 시 처음에 갈 곳 */}
        <Route path="/" element={<Navigate to="/approval-status" replace />} />
        <Route path="/approval-status" element={<ApprovalStatus />} />

        {/* 나중에 추가될 다른 메뉴들 */}
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/store" element={<div>상점 관리 페이지 (준비중)</div>} />
        <Route
          path="/products"
          element={<div>상품 관리 페이지 (준비중)</div>}
        />
      </Route>
    </Routes>
  );
}

export default App;
