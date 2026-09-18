// 웹 데모 빌드 전용. FCM 디바이스 토큰은 네이티브 기기에서만 발급된다.
// 웹에서 원본을 그대로 부르면 getDevicePushTokenAsync가 던지고, 로그인 핸들러가
// await로 물려 있어 로그인 자체가 실패한다 — 토큰 등록만 건너뛰고 로그인은 통과시킨다.
export async function registerForPushNotificationsAsync() {
  return null;
}
