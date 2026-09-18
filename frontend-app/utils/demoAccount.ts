import { Alert, Platform } from 'react-native';
import { client } from '../api/client';
import { saveTokens } from './secureStore';

// 공모전 심사자가 별도 가입·소셜 로그인 없이 핵심 기능을 바로 체험하게 하는 경로다.
// 백엔드에 데모 전용 우회 엔드포인트를 만들지 않고, 미리 만들어 둔 일반 계정으로
// 평범한 /auth/login을 호출한다 — 인증 로직에 구멍을 내지 않고 데모만 얹는 방식이다.
//
// 자격증명은 EAS/빌드 환경변수로 주입한다. 값이 비어 있으면 데모 진입 자체가 꺼지므로
// 네이티브 프로덕션 빌드에는 영향이 없다.
const DEMO_EMAIL = process.env.EXPO_PUBLIC_DEMO_EMAIL || '';
const DEMO_PASSWORD = process.env.EXPO_PUBLIC_DEMO_PASSWORD || '';

// 데모 자동 로그인은 웹 빌드에서만 켠다. 네이티브 앱은 실제 사용자 계정으로 동작해야 한다.
export const isDemoLoginEnabled =
  Platform.OS === 'web' && DEMO_EMAIL.length > 0 && DEMO_PASSWORD.length > 0;

export const demoEmail = DEMO_EMAIL;

/**
 * 데모 계정은 심사자 전원이 동시에 쓰는 하나의 계정이다. 한 사람이 계정 상태를 바꾸면
 * 그 뒤로 들어오는 모든 사람의 화면이 함께 망가진다. 실제로 겪은 것들:
 *
 *   비밀번호 변경 → 빌드에 박힌 자격증명이 무효가 되어 자동 로그인이 전부 실패한다
 *   대표 지역 변경·삭제 → 홈이 "등록된 상점이 없어요"가 된다
 *
 * 그래서 계정 상태를 바꾸는 경로는 데모에서 막는다. 조회는 전부 열어둔다.
 * 네이티브 앱은 isDemoLoginEnabled가 false라 아무 영향이 없다.
 */
export function blockIfDemo(actionName: string): boolean {
  if (!isDemoLoginEnabled) return false;

  Alert.alert(
    '체험판에서는 막혀 있어요',
    `${actionName}은(는) 여러 명이 함께 쓰는 체험 계정이라 막아두었습니다.\n` +
      '둘러보기는 그대로 하실 수 있어요.'
  );
  return true;
}

export async function loginWithDemoAccount(): Promise<boolean> {
  if (!isDemoLoginEnabled) return false;

  try {
    const response = await client.post('/auth/login', {
      email: DEMO_EMAIL,
      password: DEMO_PASSWORD,
    });

    const accessToken = response.data.data?.accessToken || response.data.accessToken;
    const refreshToken = response.data.data?.refreshToken || response.data.refreshToken;

    if (!accessToken || !refreshToken) return false;

    await saveTokens(accessToken, refreshToken);
    return true;
  } catch (error) {
    // 데모 계정이 막혔거나 서버가 죽었을 때 흰 화면으로 두지 않는다.
    // 호출부가 false를 받아 일반 로그인 화면으로 떨어뜨린다.
    console.warn('데모 계정 로그인 실패:', error);
    return false;
  }
}
