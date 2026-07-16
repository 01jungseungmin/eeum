import { createContext, useState, useContext, useEffect, useRef } from 'react';
import axios from 'axios';
import { setGlobalAnchorToken } from '../api/apiClient';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [accessToken, setAccessToken] = useState(null);
  const [hasChatRoom, setHasChatRoom] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  // Strict Mode 등으로 인해 restoreSession이 동시에 두 번 호출되는 것을 막는 잠금(Lock) 변수
  const isReissuing = useRef(false);

  // 리액트 토큰 상태가 바뀔 때마다 apiClient 측의 전역 변수를 동기화
  useEffect(() => {
    setGlobalAnchorToken(accessToken);
  }, [accessToken]);

  // 새로고침 시 딱 한 번 실행되는 세션 복구 시스템
  useEffect(() => {
    const restoreSession = async () => {
      const rfToken = localStorage.getItem('refreshToken');

      // 토큰이 없거나, 이미 다른 요청이 진행 중(true)이라면 중복 요청을 하지 않고 종료
      if (!rfToken || isReissuing.current) {
        if (!rfToken) setIsLoading(false);
        return;
      }

      try {
        isReissuing.current = true; // 🔒 요청 시작 시 플래그를 true로 설정하여 잠금

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
      } finally {
        isReissuing.current = false; // 요청이 끝나면 잠금 해제
        setIsLoading(false);
      }
    };

    restoreSession();
  }, []);

  const login = (token, role, rfToken) => {
    setAccessToken(token);
    localStorage.setItem('refreshToken', rfToken);
    localStorage.setItem('role', role);
  };

  // const login = (token, role, rfToken, hasChatRoom) => {
  //   // 💡 인자 추가
  //   setAccessToken(token);
  //   setHasChatRoom(hasChatRoom); // 💡 상태 저장
  //   localStorage.setItem('refreshToken', rfToken);
  //   localStorage.setItem('role', role);
  //   localStorage.setItem('hasChatRoom', hasChatRoom); // 💡 영속화
  // };

  const logout = () => {
    setAccessToken(null);
    localStorage.clear();
    window.location.href = '/login';
  };

  return (
    <AuthContext.Provider
      value={{ accessToken, login, logout, isLoading, hasChatRoom }}
    >
      {!isLoading && children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
