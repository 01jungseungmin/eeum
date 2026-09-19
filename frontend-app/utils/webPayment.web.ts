import { PAYMENT_REDIRECT_URL } from './paymentCompletion';

/**
 * 웹 데모용 포트원 결제.
 *
 * 네이티브 체크아웃은 포트원 브라우저 SDK를 담은 HTML을 react-native-webview 안에
 * 띄우는데, 이 라이브러리는 웹을 지원하지 않는다 — 웹에서는 결제창 대신 빈 화면이 뜬다.
 * 브라우저에서는 웹뷰가 애초에 필요 없다. 같은 SDK를 페이지에서 직접 부르면 된다.
 *
 * 결제 흐름 두 가지를 모두 받는다.
 *   PC   : 결제창이 팝업/iframe으로 뜨고 requestPayment 가 결과를 돌려준다 → 여기서 바로 검증
 *   모바일: 결제창이 페이지를 통째로 가져가고 redirectUrl 로 돌아온다 → /payment/success 가 이어받는다
 *
 * 검증·Webhook은 앱과 똑같은 경로(POST /payments/verify)를 타므로 백엔드는 손대지 않는다.
 */

export interface WebPaymentRequest {
  paymentId: string;
  orderName: string;
  totalAmount: number;
  payMethod: string;
  easyPayProvider?: string;
  customerName: string;
  customerPhone: string;
  customerEmail: string;
}

export type WebPaymentResult =
  | { status: 'success'; paymentId: string }
  | { status: 'failed'; message: string }
  | { status: 'redirecting' };

const STORE_ID = process.env.EXPO_PUBLIC_PORTONE_STORE_ID || '';
const CHANNEL_KEY = process.env.EXPO_PUBLIC_PORTONE_DEFAULT_KEY || '';

/**
 * 웹 결제는 명시적으로 켜야 동작한다.
 *
 * 심사자가 누르는 버튼이 곧바로 실결제가 되면 안 된다. 포트원 콘솔에서 테스트 채널
 * 키가 들어갔다는 걸 확인한 뒤에만 이 값을 true 로 둔다. 켜지 않으면 체크아웃이
 * "데모에서는 결제까지는 안 된다"는 안내를 띄우고 멈춘다.
 */
export const isWebPaymentEnabled = process.env.EXPO_PUBLIC_DEMO_PAYMENT_ENABLED === 'true';

export const isWebPaymentSupported = isWebPaymentEnabled && !!STORE_ID && !!CHANNEL_KEY;

// 켤 의사는 있는데 키가 비어 있는 상태를 따로 알아야 한다 — 이건 설정 실수다.
export const isWebPaymentBlocked = !isWebPaymentSupported;

const SDK_URL = 'https://cdn.portone.io/v2/browser-sdk.js';

let sdkPromise: Promise<any> | null = null;

// SDK는 번들에 넣지 않고 필요할 때 한 번만 받아온다. 결제 화면에 들어오지 않는
// 심사자에게까지 외부 스크립트를 로드시킬 이유가 없다.
function loadPortOne(): Promise<any> {
  if (sdkPromise) return sdkPromise;

  sdkPromise = new Promise((resolve, reject) => {
    const existing = (window as any).PortOne;
    if (existing) {
      resolve(existing);
      return;
    }

    const script = document.createElement('script');
    script.src = SDK_URL;
    script.async = true;
    script.onload = () => {
      const sdk = (window as any).PortOne;
      if (sdk) resolve(sdk);
      else reject(new Error('포트원 SDK를 불러왔지만 PortOne 전역이 없습니다.'));
    };
    script.onerror = () => {
      // 다음 시도에서 다시 받을 수 있게 캐시를 비운다.
      sdkPromise = null;
      reject(new Error('포트원 결제 모듈을 불러오지 못했습니다.'));
    };

    document.head.appendChild(script);
  });

  return sdkPromise;
}

export async function requestWebPayment(request: WebPaymentRequest): Promise<WebPaymentResult> {
  if (!isWebPaymentSupported) {
    return { status: 'failed', message: '웹 결제가 설정되어 있지 않습니다.' };
  }

  let PortOne: any;
  try {
    PortOne = await loadPortOne();
  } catch (error: any) {
    return { status: 'failed', message: error?.message || '결제 모듈 로딩에 실패했습니다.' };
  }

  try {
    const response = await PortOne.requestPayment({
      storeId: STORE_ID,
      channelKey: CHANNEL_KEY,
      paymentId: request.paymentId,
      orderName: request.orderName,
      totalAmount: request.totalAmount,
      currency: 'CURRENCY_KRW',
      payMethod: request.payMethod,
      ...(request.payMethod === 'EASY_PAY' && request.easyPayProvider
        ? { easyPay: { easyPayProvider: request.easyPayProvider } }
        : {}),
      customer: {
        fullName: request.customerName,
        phoneNumber: request.customerPhone,
        email: request.customerEmail,
      },
      redirectUrl: PAYMENT_REDIRECT_URL,
    });

    // 리다이렉트로 빠지는 흐름에서는 여기까지 오기 전에 페이지가 넘어간다.
    // 그래도 돌아오는 경우가 있어(브라우저·결제수단별 차이) 빈 응답을 실패로 보지 않는다.
    if (!response) {
      return { status: 'redirecting' };
    }

    // 실패·취소는 예외가 아니라 code 가 실린 응답으로 온다. paymentId도 같이 오므로
    // code를 먼저 보지 않으면 취소된 결제를 검증하러 보내게 된다.
    if (response.code != null) {
      return { status: 'failed', message: response.message || '결제가 완료되지 않았습니다.' };
    }

    const paymentId = response.paymentId || request.paymentId;
    return { status: 'success', paymentId };
  } catch (error: any) {
    return { status: 'failed', message: error?.message || '결제 중 오류가 발생했습니다.' };
  }
}
