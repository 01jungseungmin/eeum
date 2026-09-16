import { createContext, useState, useContext, useEffect, useRef } from 'react';
import {
  setGlobalAnchorToken,
  onTokenChange,
  reissueAccessToken,
} from '../api/apiClient';
import { aiManagerApi } from '../api/owner/aiManagerApi';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [accessToken, setAccessToken] = useState(null);
  const [hasChatRoom, setHasChatRoom] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [aiPlanType, setAiPlanType] = useState(null);

  // Strict Mode 등으로 인한 중복 reissue 요청 방지용 Lock
  const isReissuing = useRef(false);
  // 세션당 AI 플랜 조회 1회만 수행하기 위한 Lock
  const hasFetchedPlan = useRef(false);

  useEffect(() => {
    setGlobalAnchorToken(accessToken);
  }, [accessToken]);

  // apiClient의 401 인터셉터가 조용히 토큰을 재발급하는 경우에도(요청 흐름 중이라
  // 이 컴포넌트를 거치지 않음) React state가 같이 갱신되도록 리스너를 등록.
  // 그래야 Sidebar의 SSE 연결처럼 accessToken을 구독하는 곳들이 재발급 시점에
  // 새 토큰으로 다시 연결할 수 있다.
  useEffect(() => {
    onTokenChange((token) => setAccessToken(token));
    return () => onTokenChange(null);
  }, []);

  // 로그인/세션 복구 완료 시 AI 플랜 구독 상태를 1회 조회해 캐싱
  // (사장 계정이 아니면 404/403이 날 수 있으므로 실패는 조용히 무시)
  useEffect(() => {
    if (!accessToken || hasFetchedPlan.current) return;
    if (sessionStorage.getItem('role') !== 'ROLE_OWNER') return;

    hasFetchedPlan.current = true;

    aiManagerApi
      .getPlans()
      .then((response) => {
        const currentPlan = response.data?.data?.currentPlan;
        if (currentPlan) setAiPlanType(currentPlan);
      })
      .catch((error) => {
        console.error('AI 플랜 조회 실패:', error);
      });
  }, [accessToken]);

  // 새로고침 시 토큰 복구 시스템
  useEffect(() => {
    const restoreSession = async () => {
      const rfToken = localStorage.getItem('refreshToken');

      if (!rfToken || isReissuing.current) {
        if (!rfToken) setIsLoading(false);
        return;
      }

      try {
        isReissuing.current = true;

        const res = await reissueAccessToken(rfToken);

        if (res.data?.success || res.status === 200) {
          // 💡 백엔드 reissue 응답에 role, storeId 등을 포함시켜 받아옵니다.
          const {
            accessToken: newAt,
            refreshToken: newRf,
            role,
            myStoreId,
            hasChatRoom,
          } = res.data.data;

          setAccessToken(newAt);

          // 💡 창을 다시 열어서 sessionStorage가 비어있어도, 재발급받은 정보로 다시 채워줍니다!
          if (role) sessionStorage.setItem('role', role);
          if (myStoreId) sessionStorage.setItem('my_store_id', myStoreId);

          if (hasChatRoom !== undefined) {
            setHasChatRoom(hasChatRoom);
            sessionStorage.setItem('storeChatRoomCreated', String(hasChatRoom));
          }

          if (newRf) localStorage.setItem('refreshToken', newRf);
        }
      } catch (error) {
        console.error('새로고침/재접속 복구 실패:', error);
        localStorage.removeItem('refreshToken');
        sessionStorage.clear();
      } finally {
        isReissuing.current = false;
        setIsLoading(false);
      }
    };

    restoreSession();
  }, []);

  const login = (token, role, rfToken, storeData = {}) => {
    setAccessToken(token);

    // refreshToken은 localStorage에 저장
    localStorage.setItem('refreshToken', rfToken);

    // 그 외는 sessionStorage에 저장
    sessionStorage.setItem('role', role);

    if (storeData.myStoreId) {
      sessionStorage.setItem('my_store_id', storeData.myStoreId);
    }
    if (storeData.storeChatRoomId) {
      sessionStorage.setItem('storeChatRoom_id', storeData.storeChatRoomId);
    }
    if (storeData.hasChatRoom !== undefined) {
      setHasChatRoom(storeData.hasChatRoom);
      sessionStorage.setItem(
        'storeChatRoomCreated',
        String(storeData.hasChatRoom),
      );
    }
  };

  // 💡 로그아웃 (전체 스토리지 정돈)
  const logout = () => {
    setAccessToken(null);
    setHasChatRoom(false);
    setAiPlanType(null);
    hasFetchedPlan.current = false;

    // refreshToken 및 모든 세션 데이터 완전 삭제
    localStorage.removeItem('refreshToken');
    sessionStorage.clear();

    window.location.href = '/login';
  };

  return (
    <AuthContext.Provider
      value={{ accessToken, login, logout, isLoading, hasChatRoom, aiPlanType }}
    >
      {!isLoading && children}
    </AuthContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => useContext(AuthContext);
