// 네이티브 빌드용 빈 구현. 실제 동작은 mobileFrame.web.ts 에만 있다.
// 진입점(app/_layout.tsx)이 플랫폼 분기 없이 부를 수 있게 같은 이름을 내보낸다.

export const MOBILE_FRAME_WIDTH = 430;

export function applyMobileFrame(): void {}
