import { apiClient } from '../apiClient';

export const customerApi = {
  // 통합 고객 목록 조회
  getCustomers: (params = {}) => {
    // { customerType: 'ALL', interestType: 'ALL', sort: 'RECENT_ORDER_DESC', page: 0, size: 10 }
    return apiClient.get('/owner/stores/me/customers', { params });
  },
};
