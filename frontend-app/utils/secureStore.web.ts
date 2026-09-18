// 웹 데모 빌드 전용. expo-secure-store는 웹을 지원하지 않아 import 시점에 터진다.
// 심사용 웹 데모는 기기 키체인이 없는 브라우저에서 돌아가야 하므로 localStorage로 대신한다.
// 네이티브 앱은 secureStore.ts(키체인/Keystore)를 그대로 쓴다 — 이 파일은 웹 번들에만 들어간다.

const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

function getStorage(): Storage | null {
  // SSR·프리렌더 단계에는 window가 없다. 여기서 던지면 번들 평가가 통째로 멈춘다.
  if (typeof window === 'undefined') return null;
  try {
    return window.localStorage;
  } catch {
    // 시크릿 모드 등에서 접근 자체가 차단될 수 있다. 심사자가 시크릿 모드로 열어보는
    // 것이 공지 권장 사항이라 이 경로는 실제로 밟힌다.
    return null;
  }
}

export async function saveTokens(accessToken: string, refreshToken: string) {
  const storage = getStorage();
  if (!storage) return;
  storage.setItem(ACCESS_TOKEN_KEY, accessToken);
  storage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export async function getAccessToken() {
  return getStorage()?.getItem(ACCESS_TOKEN_KEY) ?? null;
}

export async function getRefreshToken() {
  return getStorage()?.getItem(REFRESH_TOKEN_KEY) ?? null;
}

export async function clearTokens() {
  const storage = getStorage();
  if (!storage) return;
  storage.removeItem(ACCESS_TOKEN_KEY);
  storage.removeItem(REFRESH_TOKEN_KEY);
}
