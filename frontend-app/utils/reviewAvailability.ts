/**
 * 주문 리뷰를 언제 쓸 수 있는지 한 곳에서 정한다.
 *
 * 백엔드는 COMPLETED 주문만 받는다(StoreReviewService.createReview).
 * COMPLETED 로 가려면 사장이 확인 → 준비 완료 → 수령 완료를 모두 눌러야 한다.
 *
 * 그전까지 화면에 아무것도 안 띄우면 "리뷰 기능이 없나?"로 읽힌다. 실제로
 * 결제를 마친 사용자가 구매 내역에서 리뷰 버튼을 못 찾는 일이 있었다.
 * 그래서 못 쓰는 동안에도 '왜 아직 못 쓰는지'를 보여준다.
 */

export type ReviewAvailability =
  /** 지금 쓸 수 있다 */
  | { state: 'available' }
  /** 이미 썼다 */
  | { state: 'done'; message: string }
  /** 아직이다 — 이유를 보여준다 */
  | { state: 'pending'; message: string }
  /** 리뷰 대상이 아니다 (취소·만료). 아무것도 띄우지 않는다 */
  | { state: 'none' };

// 수령 전 단계별 안내. 사장이 어디까지 처리했는지 그대로 알려준다.
const PENDING_MESSAGE: Record<string, string> = {
  PENDING: '상품을 받은 뒤에 리뷰를 쓸 수 있어요.',
  PAID: '상품을 받은 뒤에 리뷰를 쓸 수 있어요.',
  CONFIRMED: '사장님이 주문을 확인했어요. 수령 후 리뷰를 쓸 수 있어요.',
  READY: '준비가 끝났어요. 수령 후 리뷰를 쓸 수 있어요.',
};

export function getReviewAvailability(status: string, hasReview: boolean): ReviewAvailability {
  if (status === 'COMPLETED') {
    return hasReview ? { state: 'done', message: '리뷰 작성 완료' } : { state: 'available' };
  }

  const message = PENDING_MESSAGE[status];
  if (message) {
    return { state: 'pending', message };
  }

  // CANCELLED · EXPIRED 등 — 거래가 성립하지 않았으니 리뷰 얘기를 꺼내지 않는다.
  return { state: 'none' };
}
