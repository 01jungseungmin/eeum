import { apiClient } from '../apiClient';

export const orderApi = {
  // 전체 주문 목록 조회
  getOwnerOrders: (params) => {
    return apiClient.get('/owner/orders', { params });
  },

  // 주문 상세 정보 조회
  getOwnerOrderDetail: (orderId) => {
    return apiClient.get(`/owner/orders/${orderId}`);
  },
};
