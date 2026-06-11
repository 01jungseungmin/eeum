import axios from 'axios';

export let currentAccessToken = null;

export const setGlobalAnchorToken = (token) => {
  currentAccessToken = token;
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
          // 여기서 직접 순수 axios로 호출하여 순환 참조를 원천 차단합니다.
          const res = await axios.post(
            'http://localhost:8080/auth/token/reissue',
            {
              refreshToken: storedRefreshToken,
            },
          );

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
