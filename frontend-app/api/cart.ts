import { client } from './client';

export const cartApi = {
  // 1. 장바구니 조회
  getCart: async () => {
    const response = await client.get('/cart');
    return response.data?.data || response.data;
  },

  // 2. 장바구니 상품 추가
  addCartItem: async (body: { 
    productId?: number; 
    eventProductId?: number; 
    selectedOptionItemIds?: number[]; 
    quantity: number 
  }) => {
    const response = await client.post('/cart/items', body);
    return response.data?.data || response.data;
  },

  // 3. 장바구니 상품 수량 변경
  updateQuantity: async (cartItemId: number, quantity: number) => {
    const response = await client.patch(`/cart/items/${cartItemId}`, { quantity });
    return response.data?.data || response.data;
  },

  // 4. 장바구니 상품 개별 삭제
  removeCartItem: async (cartItemId: number) => {
    const response = await client.delete(`/cart/items/${cartItemId}`);
    return response.data?.data || response.data;
  },

  // 5. 장바구니 전체 비우기
  clearCart: async () => {
    const response = await client.delete('/cart');
    return response.data?.data || response.data;
  }
};