import { Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const ProtectedRoute = ({ children }) => {
  const { accessToken } = useAuth();
  const refreshToken = localStorage.getItem('refreshToken');

  // 메모리에 accessToken도 없고, 스토리지에 refreshToken도 없으면 로그인 페이지로 이동
  if (!accessToken && !refreshToken) {
    return <Navigate to="/login" replace />;
  }

  return children;
};

export default ProtectedRoute;
