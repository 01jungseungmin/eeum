import { Alert } from 'react-native';
import type { AlertButton } from 'react-native';

// react-native-web의 Alert은 구현이 비어 있다 (`class Alert { static alert() {} }`).
// 앱 25개 화면이 Alert.alert로 확인창·에러·완료 알림을 띄우는데, 웹에서는 전부
// 조용히 사라진다 — 로그아웃 버튼을 눌러도 아무 일도 안 일어나는 것처럼 보인다.
//
// 화면 25개를 고치는 대신 진입점에서 한 번 갈아끼운다. 호출부는 그대로 두고
// 브라우저 기본 대화상자로 같은 계약(버튼 콜백)을 지킨다.

export function applyWebAlertPatch() {
  if (typeof window === 'undefined') return;

  Alert.alert = (
    title: string,
    message?: string,
    buttons?: AlertButton[],
    _options?: unknown
  ) => {
    const text = [title, message].filter(Boolean).join('\n\n');

    // 버튼이 없거나 하나뿐이면 확인만 받으면 된다.
    if (!buttons || buttons.length === 0) {
      window.alert(text);
      return;
    }

    if (buttons.length === 1) {
      window.alert(text);
      buttons[0].onPress?.();
      return;
    }

    // 취소 버튼은 style로 찾는다. 명시가 없으면 RN 관례대로 마지막 버튼을 주 동작으로 본다.
    const cancelButton = buttons.find((button) => button.style === 'cancel');
    const confirmButton =
      buttons.filter((button) => button.style !== 'cancel').pop() ?? buttons[buttons.length - 1];

    if (window.confirm(text)) {
      confirmButton?.onPress?.();
    } else {
      cancelButton?.onPress?.();
    }
  };
}
