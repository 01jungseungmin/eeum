import { apiClient } from '../apiClient';

export const customerApi = {
  // 통합 고객 목록 조회
  getCustomers: (params = {}) => {
    return apiClient.get('/owner/stores/me/customers', { params });
  },

  // 고객의 주문 내역 조회
  getCustomerOrders: (customerId) => {
    return apiClient.get(`/owner/stores/me/customers/${customerId}/orders`);
  },

  // 고객 통계 요약 조회
  getCustomerSummary: () => {
    return apiClient.get('/owner/stores/me/customers/summary');
  },
};
