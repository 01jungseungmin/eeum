import { createContext, useState, useContext, useEffect } from 'react';
import axios from 'axios';
import { setGlobalAnchorToken } from '../api/apiClient';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [accessToken, setAccessToken] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  // 리액트 토큰 상태가 바뀔 때마다 apiClient 측의 전역 변수를 동기화
  useEffect(() => {
    setGlobalAnchorToken(accessToken);
  }, [accessToken]);

  // 새로고침 시 딱 한 번 실행되는 세션 복구 시스템
  useEffect(() => {
    const restoreSession = async () => {
      const rfToken = localStorage.getItem('refreshToken');

      if (rfToken) {
        try {
          const res = await axios.post(
            'http://localhost:8080/auth/token/reissue',
            {
              refreshToken: rfToken,
            },
          );

          if (res.data?.success || res.status === 200) {
            const { accessToken: newAt, refreshToken: newRf } = res.data.data;
            setAccessToken(newAt); // 리액트 메모리 복구
            if (newRf) localStorage.setItem('refreshToken', newRf);
          }
        } catch (error) {
          console.error('새로고침 복구 실패:', error);
          localStorage.clear();
        }
      }
      setIsLoading(false);
    };

    restoreSession();
  }, []);

  const login = (token, role, rfToken) => {
    setAccessToken(token);
    localStorage.setItem('refreshToken', rfToken);
    localStorage.setItem('role', role);
  };

  const logout = () => {
    setAccessToken(null);
    localStorage.clear();
    window.location.href = '/login';
  };

  return (
    <AuthContext.Provider value={{ accessToken, login, logout, isLoading }}>
      {!isLoading && children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
