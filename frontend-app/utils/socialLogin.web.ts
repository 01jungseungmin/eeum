// 웹 데모 빌드 전용. 카카오·네이버 네이티브 SDK는 브라우저에 존재하지 않는다.
// 웹에서는 로그인 화면이 이 플래그를 보고 소셜 버튼을 아예 렌더링하지 않으므로
// 아래 함수들은 호출되지 않지만, 잘못 불렸을 때 조용히 실패하지 않도록 던진다.

export const isSocialLoginAvailable = false;

export async function loginWithKakao(): Promise<string> {
  throw new Error('카카오 로그인은 네이티브 앱에서만 지원합니다.');
}

export async function loginWithNaver(): Promise<string | null> {
  throw new Error('네이버 로그인은 네이티브 앱에서만 지원합니다.');
}
