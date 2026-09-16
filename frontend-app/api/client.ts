import axios, { 
  AxiosInstance, 
  InternalAxiosRequestConfig, 
  AxiosError, 
  AxiosResponse 
} from 'axios';
import { getAccessToken, getRefreshToken, saveTokens, clearTokens } from '../utils/secureStore';

const BASE_URL = process.env.EXPO_PUBLIC_API_URL || '';

//Axios 기본 설정에 '_retry' 속성을 추가.
interface CustomAxiosRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

// 기본 API 설정
export const client: AxiosInstance = axios.create({
  baseURL: BASE_URL, 
});

// 1. 요청(Request) 인터셉터
client.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    const token = await getAccessToken(); 
    
    if (token) {
      config.headers = config.headers || {};
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error: any) => {
    return Promise.reject(error);
  }
);

// 401이 동시에 여러 개 터져도 재발급 요청은 하나만 나가도록 공유하는 Promise.
// 없으면 각 요청이 같은(아직 회전 전) Refresh Token으로 따로 재발급을 시도하다가,
// 먼저 끝난 요청이 토큰을 회전시켜 나머지가 실패 → 방금 재발급된 세션까지 로그아웃되어 버린다.
let refreshPromise: Promise<string> | null = null;

const refreshAccessToken = async (): Promise<string> => {
  const refreshToken = await getRefreshToken();
  if (!refreshToken) throw new Error("리프레시 토큰이 없습니다.");

  // 재발급 요청 (인터셉터가 안 걸리게 기본 axios 사용)
  const refreshResponse = await axios.post(`${BASE_URL}/auth/token/reissue`, {
    refreshToken: refreshToken
  });

  const newAccessToken = refreshResponse.data.data?.accessToken;
  const newRefreshToken = refreshResponse.data.data?.refreshToken;

  if (refreshResponse.status !== 200 || !newAccessToken || !newRefreshToken) {
    throw new Error("토큰 재발급 응답이 올바르지 않습니다.");
  }

  await saveTokens(newAccessToken, newRefreshToken);
  return newAccessToken;
};

// 2. 응답(Response) 인터셉터
client.interceptors.response.use(
  (response: AxiosResponse) => {
    return response;
  },
  async (error: AxiosError) => {
    const originalRequest = error.config as CustomAxiosRequestConfig;

    if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        // 이미 재발급이 진행 중이면 그 결과를 같이 기다린다. 새로 만들지 않는다.
        if (!refreshPromise) {
          refreshPromise = refreshAccessToken().finally(() => {
            refreshPromise = null;
          });
        }
        const newAccessToken = await refreshPromise;

        // 원래 요청 헤더에 새 토큰 끼워넣기
        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        }

        return client(originalRequest);
      } catch (refreshError) {
        console.log("토큰 재발급 실패. 다시 로그인해야 합니다.");
        await clearTokens();
        // 필요 시 화면 이동 로직 추가 가능
      }
    }

    return Promise.reject(error);
  }
);