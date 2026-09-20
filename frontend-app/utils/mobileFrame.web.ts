import { Dimensions } from 'react-native';
import type { ScaledSize } from 'react-native';

/**
 * PC 브라우저에서 앱을 폰 너비 틀 안에만 그린다. 공모전 심사자가 노트북으로
 * 들어와도 실제 앱과 같은 화면을 보게 하는 웹 데모 전용 장치다.
 *
 * 두 가지를 같이 해야 한다. 하나만 하면 화면이 깨진다.
 *
 *   1. CSS로 #root 를 430px 로 묶는다 (아래 FRAME_CSS)
 *   2. Dimensions 가 읽는 너비도 같이 줄인다 (아래 patchDimensions)
 *
 * 2번이 핵심이다. 배너 캐러셀·상품/상점/중고 상세 등은 Dimensions.get('window').width
 * 로 이미지 크기를 직접 계산한다. CSS로 틀만 줄이면 이 이미지들이 브라우저 전체
 * 너비(1440px)로 그려져 틀 밖으로 삐져나간다. 여기서 한 번 보정하면 그 화면들은
 * 손대지 않아도 된다.
 *
 * 폰으로 접속하면 화면이 430px보다 좁아 클램프가 걸리지 않으므로 지금과 똑같이 보인다.
 * 네이티브 앱은 이 파일이 번들에 들어가지 않아 아무 영향이 없다.
 */
export const MOBILE_FRAME_WIDTH = 430;

const FRAME_STYLE_ID = 'eeum-mobile-frame';

// 틀은 화면이 430px보다 넓을 때만 그린다. 폰에서는 규칙 자체가 적용되지 않는다.
const FRAME_CSS = `
  html, body { height: 100%; margin: 0; }
  body { background-color: #DFE3E8; }
  #root { background-color: #FFFFFF; }

  @media (min-width: ${MOBILE_FRAME_WIDTH + 1}px) {
    #root {
      max-width: ${MOBILE_FRAME_WIDTH}px;
      margin: 0 auto;
      height: 100%;
      overflow: hidden;
      box-shadow: 0 0 24px rgba(0, 0, 0, 0.18);
    }
  }
`;

function injectFrameStyle() {
  if (document.getElementById(FRAME_STYLE_ID)) return;

  const style = document.createElement('style');
  style.id = FRAME_STYLE_ID;
  style.textContent = FRAME_CSS;
  document.head.appendChild(style);
}

// 같은 크기에는 같은 객체를 돌려준다. 매번 새 객체를 만들면 useWindowDimensions가
// 값이 안 바뀌었는데도 리렌더를 한 번씩 더 돈다.
const clampCache = new WeakMap<ScaledSize, ScaledSize>();

function clamp(size: ScaledSize): ScaledSize {
  if (size.width <= MOBILE_FRAME_WIDTH) return size;

  const cached = clampCache.get(size);
  if (cached) return cached;

  // 높이는 그대로 둔다. 틀은 좌우만 좁히고 세로는 브라우저 전체를 쓴다.
  const clamped: ScaledSize = { ...size, width: MOBILE_FRAME_WIDTH };
  clampCache.set(size, clamped);
  return clamped;
}

function patchDimensions() {
  // removeEventListener는 react-native 타입에서 빠졌지만 react-native-web에는 살아 있고,
  // useWindowDimensions가 구독 해제에 그걸 쓴다. 타입이 아니라 실제 구현을 따라간다.
  const rnwDimensions = Dimensions as unknown as {
    removeEventListener?: (type: string, handler: (payload: any) => void) => void;
  };

  const originalGet = Dimensions.get.bind(Dimensions);
  const originalAdd = Dimensions.addEventListener.bind(Dimensions);
  const originalRemove = rnwDimensions.removeEventListener?.bind(Dimensions);

  Dimensions.get = (dimension: 'window' | 'screen') => clamp(originalGet(dimension));

  // 리사이즈 이벤트로 흘러오는 값도 같이 보정해야 한다. get만 고치면 창 크기를
  // 바꾸는 순간 구독자들이 보정 안 된 1440px을 받아 화면이 다시 깨진다.
  const wrapped = new WeakMap<Function, (payload: any) => void>();

  Dimensions.addEventListener = ((type: string, handler: (payload: any) => void) => {
    const wrapper = (payload: { window?: ScaledSize; screen?: ScaledSize }) =>
      handler({
        ...payload,
        window: payload.window ? clamp(payload.window) : payload.window,
        screen: payload.screen ? clamp(payload.screen) : payload.screen,
      });

    wrapped.set(handler, wrapper);
    return originalAdd(type as any, wrapper as any);
  }) as typeof Dimensions.addEventListener;

  // 해제는 원본 핸들러로 들어온다 (react-native-web의 useWindowDimensions가 그렇게 부른다).
  // 감싼 쪽을 찾아 넘기지 않으면 구독이 영영 안 풀린다.
  if (originalRemove) {
    rnwDimensions.removeEventListener = (type: string, handler: (payload: any) => void) =>
      originalRemove(type, wrapped.get(handler) ?? handler);
  }
}

let applied = false;

export function applyMobileFrame(): void {
  if (applied || typeof window === 'undefined') return;
  applied = true;

  injectFrameStyle();
  patchDimensions();
}
