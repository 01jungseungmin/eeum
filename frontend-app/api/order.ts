import { client } from './client';

export const orderApi = {
  // 1. 주문서 생성 (결제 전, 장바구니 데이터를 바탕으로 DB에 주문 정보 먼저 생성)
  createOrder: async (cartId: number) => {
    const response = await client.post('/orders', { cartId });
    return response.data?.data || response.data;
  },

  // 2. 결제 검증 요청
  verifyPayment: async (paymentId: string, orderNumber: string) => {
    const response = await client.post('/payments/verify', {
      paymentId: paymentId,
      orderNumber: orderNumber,
    });
    return response.data?.data || response.data;
  }
};