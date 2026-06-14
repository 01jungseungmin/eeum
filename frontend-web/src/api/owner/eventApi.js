import { apiClient } from '../apiClient';

export const eventApi = {
  // 상점의 이벤트 상품 목록 조회 / 등록 / 수정 / 삭제
  getOwnerEventProducts: () => {
    return apiClient.get('/owner/event-products');
  },
  createOwnerEventProduct: (eventData) => {
    return apiClient.post('/owner/event-products', eventData);
  },
  updateOwnerEventProduct: (eventProductId, eventData) => {
    return apiClient.patch(
      `/owner/event-products/${eventProductId}`,
      eventData,
    );
  },
  deleteOwnerEventProduct: (eventProductId) => {
    return apiClient.delete(`/owner/event-products/${eventProductId}`);
  },

  // 잠시 여기서 불러오기
  getOwnerProducts: () => {
    return apiClient.get('/owner/products');
  },
};
