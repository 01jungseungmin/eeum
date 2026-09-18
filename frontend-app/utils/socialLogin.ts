import * as KakaoLogin from '@react-native-seoul/kakao-login';
import NaverLogin from '@react-native-seoul/naver-login';

// 카카오·네이버 SDK는 네이티브 모듈이라 import만으로 웹 번들을 깨뜨린다.
// 화면에서 직접 import하지 않고 이 래퍼를 거치게 해서, 웹은 socialLogin.web.ts가
// 대신 잡히도록 한다.

export const isSocialLoginAvailable = true;

export async function loginWithKakao(): Promise<string> {
  const result = await KakaoLogin.login();
  return result.accessToken;
}

export async function loginWithNaver(): Promise<string | null> {
  NaverLogin.initialize({
    appName: 'EEUM',
    consumerKey: process.env.EXPO_PUBLIC_NAVER_CLIENT_ID as string,
    consumerSecret: process.env.EXPO_PUBLIC_NAVER_CLIENT_SECRET as string,
    serviceUrlSchemeIOS: 'eeum',
  });

  const { successResponse } = await NaverLogin.login();
  return successResponse?.accessToken ?? null;
}
