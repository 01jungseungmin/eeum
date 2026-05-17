import * as SecureStore from 'expo-secure-store';

const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

// 1. 토큰 저장하기 (두 개를 한 번에 저장)
export async function saveTokens(accessToken: string, refreshToken: string) {
  try {
    await SecureStore.setItemAsync(ACCESS_TOKEN_KEY, accessToken);
    await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, refreshToken);
    console.log('토큰 2개 저장 완료');
  } catch (error) {
    console.error('토큰 저장 실패:', error);
  }
}

// 2. Access 토큰 불러오기
export async function getAccessToken() {
  try {
    return await SecureStore.getItemAsync(ACCESS_TOKEN_KEY);
  } catch (error) {
    return null;
  }
}

// 3. Refresh 토큰 불러오기
export async function getRefreshToken() {
  try {
    return await SecureStore.getItemAsync(REFRESH_TOKEN_KEY);
  } catch (error) {
    return null;
  }
}

// 4. 토큰 삭제하기 (로그아웃 시)
export async function clearTokens() {
  try {
    await SecureStore.deleteItemAsync(ACCESS_TOKEN_KEY);
    await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
    console.log('토큰 삭제 완료');
  } catch (error) {
    console.error('토큰 삭제 실패:', error);
  }
}