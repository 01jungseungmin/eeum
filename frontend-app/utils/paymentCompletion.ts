import AsyncStorage from '@react-native-async-storage/async-storage';

import { orderApi } from '../api/order';

/**
 * 결제 완료 후 돌아올 주소.
 *
 * 웹뷰 안에서 끝나는 결제(카드 등)는 http://localhost 로도 낚아챌 수 있지만,
 * 토스·카카오페이처럼 외부 앱으로 전환되는 결제는 그 앱이 http://localhost 로
 * 우리 앱을 다시 열 방법이 없다. 결제는 됐는데 사용자는 결제앱에 갇히고
 * 검증 호출이 누락돼 주문이 미완료로 남는다. 앱 스킴을 써야 복귀가 성립한다.
 */
export const PAYMENT_REDIRECT_URL = 'eeum://payment/success';

// 외부 결제앱이 떠 있는 동안 OS가 우리 앱을 메모리에서 밀어낼 수 있다.
// 그 경우 복귀는 콜드 스타트라 화면 state가 전부 날아가므로 주문 정보를 따로 남긴다.
const PENDING_ORDER_KEY = 'payment:pendingOrder';

export interface PendingOrder {
  orderNumber: string;
  orderId: string;
  orderName: string;
  totalPrice: number;
}

// 커스텀 스킴은 URL 생성자가 쿼리를 못 뽑는 환경이 있어 직접 자른다.
export const extractPaymentId = (url: string): string | null => {
  const query = url.split('?')[1];
  if (!query) return null;
  const hit = query.split('&').find((pair) => pair.startsWith('paymentId='));
  return hit ? decodeURIComponent(hit.slice('paymentId='.length)) : null;
};

// 결제창을 열기 직전에만 쓴다. 저장 실패가 결제 자체를 막지는 않는다.
export const savePendingOrder = async (order: PendingOrder): Promise<void> => {
  try {
    await AsyncStorage.setItem(PENDING_ORDER_KEY, JSON.stringify(order));
  } catch (e) {
    console.error('결제 복귀 정보 저장 실패', e);
  }
};

export const loadPendingOrder = async (): Promise<PendingOrder | null> => {
  try {
    const raw = await AsyncStorage.getItem(PENDING_ORDER_KEY);
    return raw ? (JSON.parse(raw) as PendingOrder) : null;
  } catch (e) {
    console.error('결제 복귀 정보 조회 실패', e);
    return null;
  }
};

export const clearPendingOrder = async (): Promise<void> => {
  try {
    await AsyncStorage.removeItem(PENDING_ORDER_KEY);
  } catch (e) {
    console.error('결제 복귀 정보 삭제 실패', e);
  }
};

export type VerifyResult =
  | { status: 'success'; order: PendingOrder }
  | { status: 'no-order' }
  | { status: 'failed' };

/**
 * 결제 검증. 웹뷰 안에서 끝난 결제와 외부 앱에서 돌아온 결제가 같은 경로를 탄다.
 *
 * 주문번호는 호출자가 알고 있으면 그 값을, 콜드 스타트라 잃었으면 저장해 둔 값을
 * 쓴다. 둘 다 없으면 서버가 결제와 주문을 짝지을 수 없어 검증을 보내지 않는다.
 */
export const verifyPaymentWithPendingOrder = async (
  paymentId: string,
  knownOrder?: PendingOrder | null
): Promise<VerifyResult> => {
  const order = knownOrder?.orderNumber ? knownOrder : await loadPendingOrder();

  if (!order?.orderNumber) {
    return { status: 'no-order' };
  }

  try {
    await orderApi.verifyPayment(paymentId, order.orderNumber);
    await clearPendingOrder();
    return { status: 'success', order };
  } catch (e) {
    console.error('결제 검증 실패', e);
    return { status: 'failed' };
  }
};
