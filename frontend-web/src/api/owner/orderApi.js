import { apiClient } from '../apiClient';

export const orderApi = {
  // 전체 주문 목록 / 상세 정보 조회
  getOwnerOrders: (params) => {
    return apiClient.get('/owner/orders', { params });
  },
  getOwnerOrderDetail: (orderId) => {
    return apiClient.get(`/owner/orders/${orderId}`);
  },

  // 주문 승인 / 거절
  confirmOrder: (orderId) => {
    return apiClient.patch(`/owner/orders/${orderId}/confirm`);
  },
  rejectOrder: (orderId, reason) => {
    return apiClient.patch(`/owner/orders/${orderId}/reject`, { reason });
  },

  // 거래 완료 (구매 주문용 / 방문 예약용)
  readyOrder: (orderId) => apiClient.patch(`/owner/orders/${orderId}/ready`),
  completeOrder: (orderId) =>
    apiClient.patch(`/owner/orders/${orderId}/complete`),
};
