// 네이티브 빌드용 빈 구현. 실제 동작은 webPayment.web.ts 에만 있다.
// 네이티브는 기존 WebView + 포트원 브라우저 SDK 경로를 그대로 쓴다.

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
  // 모바일 브라우저는 결제창이 페이지를 통째로 가져간다. 이 화면은 곧 사라지므로
  // 호출부가 아무것도 하지 않고 기다려야 한다는 뜻이다.
  | { status: 'redirecting' };

// 네이티브에서는 웹 결제 경로를 타지 않는다.
export const isWebPaymentSupported = false;

// 웹 데모에서 결제를 아예 막아둔 상태인지. 네이티브에는 해당 없음.
export const isWebPaymentBlocked = false;

export async function requestWebPayment(_request: WebPaymentRequest): Promise<WebPaymentResult> {
  return { status: 'failed', message: '이 플랫폼에서는 웹 결제를 사용하지 않습니다.' };
}
