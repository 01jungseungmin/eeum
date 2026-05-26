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
        const refreshToken = await getRefreshToken(); 
        if (!refreshToken) throw new Error("리프레시 토큰이 없습니다.");

        // 재발급 요청 (인터셉터가 안 걸리게 기본 axios 사용)
        const refreshResponse = await axios.post(`${BASE_URL}/auth/token/reissue`, {
          refreshToken: refreshToken
        });

        if (refreshResponse.status === 200) {
          const newAccessToken = refreshResponse.data.data?.accessToken;
          const newRefreshToken = refreshResponse.data.data?.refreshToken;
          
          if (newAccessToken && newRefreshToken) {
            // 1. 새 토큰 저장
            await saveTokens(newAccessToken, newRefreshToken);
            
            // 2. 원래 요청 헤더에 새 토큰 끼워넣기
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
            }
            
            return client(originalRequest);
          }
        }
      } catch (refreshError) {
        console.log("토큰 재발급 실패. 다시 로그인해야 합니다.");
        await clearTokens();
        // 필요 시 화면 이동 로직 추가 가능
      }
    }
    
    return Promise.reject(error);
  }
);