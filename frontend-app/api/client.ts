import axios, { 
  AxiosInstance, 
  InternalAxiosRequestConfig, 
  AxiosError, 
  AxiosResponse 
} from 'axios';
import { Platform } from 'react-native';
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

// 데모 자격증명은 여기서 직접 읽는다. utils/demoAccount는 이 모듈을 import하므로
// 반대로 가져오면 순환 참조가 된다.
const DEMO_EMAIL = process.env.EXPO_PUBLIC_DEMO_EMAIL || '';
const DEMO_PASSWORD = process.env.EXPO_PUBLIC_DEMO_PASSWORD || '';
const isDemoBuild = Platform.OS === 'web' && DEMO_EMAIL.length > 0 && DEMO_PASSWORD.length > 0;

// 재발급과 같은 이유로 공유한다 — 401이 여러 개 터졌을 때 재로그인도 하나만 나가야 한다.
let demoReloginPromise: Promise<string | null> | null = null;

const reloginAsDemo = async (): Promise<string | null> => {
  if (!isDemoBuild) return null;

  if (!demoReloginPromise) {
    demoReloginPromise = (async () => {
      try {
        // 인터셉터가 다시 걸리지 않게 기본 axios를 쓴다.
        const response = await axios.post(`${BASE_URL}/auth/login`, {
          email: DEMO_EMAIL,
          password: DEMO_PASSWORD,
        });

        const accessToken = response.data.data?.accessToken || response.data.accessToken;
        const refreshToken = response.data.data?.refreshToken || response.data.refreshToken;
        if (!accessToken || !refreshToken) return null;

        await saveTokens(accessToken, refreshToken);
        return accessToken as string;
      } catch (error) {
        // 데모 계정 비밀번호가 바뀌었거나 서버가 죽은 경우다. 로그인 화면으로 떨어진다.
        console.warn('데모 계정 재로그인 실패:', error);
        return null;
      } finally {
        demoReloginPromise = null;
      }
    })();
  }

  return demoReloginPromise;
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

        // 웹 데모는 심사자 전원이 계정 하나를 공유한다. 서버의 refresh:{accountId}는
        // 계정당 하나뿐이라, 누가 로그아웃하거나 새로 로그인하면 먼저 들어와 있던 사람의
        // 재발급이 실패한다 — 가만히 둘러보던 사람이 30분쯤 뒤 갑자기 빈 화면을 보게 된다.
        // 데모에서는 조용히 다시 로그인해 이어서 쓰게 한다.
        const recoveredToken = await reloginAsDemo();
        if (recoveredToken) {
          if (originalRequest.headers) {
            originalRequest.headers.Authorization = `Bearer ${recoveredToken}`;
          }
          return client(originalRequest);
        }
      }
    }

    return Promise.reject(error);
  }
);