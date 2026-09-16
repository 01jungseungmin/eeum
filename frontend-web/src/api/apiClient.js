import axios from 'axios';

export let currentAccessToken = null;

// AuthContext가 등록해두는 리스너. apiClient 내부(401 인터셉터)에서
// 토큰이 조용히 재발급될 때도 React state(accessToken)가 같이 갱신되도록
// 브릿지 역할을 한다 — 그래야 이 토큰을 구독하는 컴포넌트(Sidebar의 SSE 등)가
// 재발급 시점에 다시 렌더링/재연결될 수 있다.
let tokenChangeListener = null;

export const onTokenChange = (listener) => {
  tokenChangeListener = listener;
};

export const setGlobalAnchorToken = (token) => {
  currentAccessToken = token;
  tokenChangeListener?.(token);
};

// 토큰 재발급을 앱 전체에서 단일 진행 중 요청으로 공유한다.
// AuthContext.restoreSession()과 이 파일의 401 인터셉터가 각자 따로
// /auth/token/reissue를 호출하면, 백엔드가 refreshToken을 1회용으로 회전시키는
// 특성상 거의 동시에 두 요청이 들어갔을 때 뒤에 처리되는 쪽이 "이미 회전된 토큰"으로
// 검증에 실패해 탈취 의심 처리되며 세션 전체가 무효화된다. 두 경로가 항상 이
// 함수를 거치게 해서, 이미 재발급이 진행 중이면 새 요청을 또 보내지 않고
// 진행 중인 것과 같은 결과를 기다리게 한다.
let reissuePromise = null;

export const reissueAccessToken = (refreshToken) => {
  if (!reissuePromise) {
    reissuePromise = axios
      .post('http://localhost:8080/auth/token/reissue', { refreshToken })
      .finally(() => {
        reissuePromise = null;
      });
  }
  return reissuePromise;
};

export const apiClient = axios.create({
  baseURL: 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  (config) => {
    if (currentAccessToken) {
      config.headers.Authorization = `Bearer ${currentAccessToken}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (
      originalRequest.url.includes('/auth/token/reissue') ||
      originalRequest.url.includes('/auth/password/')
    ) {
      return Promise.reject(error);
    }

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      const storedRefreshToken = localStorage.getItem('refreshToken');

      if (storedRefreshToken) {
        try {
          const res = await reissueAccessToken(storedRefreshToken);

          if (res.data?.success || res.status === 200) {
            const { accessToken: newAt, refreshToken: newRf } = res.data.data;

            setGlobalAnchorToken(newAt);
            if (newRf) localStorage.setItem('refreshToken', newRf);

            originalRequest.headers.Authorization = `Bearer ${newAt}`;
            return apiClient(originalRequest);
          }
        } catch (refreshError) {
          console.error('세션 만료로 로그아웃됩니다.');
          localStorage.clear();
          window.location.href = '/login';
          return Promise.reject(refreshError);
        }
      }
    }
    return Promise.reject(error);
  },
);
