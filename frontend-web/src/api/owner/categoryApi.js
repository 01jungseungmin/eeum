import { apiClient } from '../apiClient';

export const categoryApi = {
  // 상품 카테고리 조회 / 생성 / 수정 / 삭제
  getOwnerProductCategories: () => {
    return apiClient.get('/owner/product-categories');
  },
  createOwnerProductCategory: (categoryData) => {
    return apiClient.post('/owner/product-categories', categoryData);
  },
  updateOwnerProductCategory: (categoryId, categoryData) => {
    return apiClient.patch(
      `/owner/product-categories/${categoryId}`,
      categoryData,
    );
  },
  deleteOwnerProductCategory: (categoryId) => {
    return apiClient.delete(`/owner/product-categories/${categoryId}`);
  },

  // 상품 카테고리 활성화 / 비활성화
  activateOwnerProductCategory: (categoryId) => {
    return apiClient.patch(`/owner/product-categories/${categoryId}/activate`);
  },
  deactivateOwnerProductCategory: (categoryId) => {
    return apiClient.patch(
      `/owner/product-categories/${categoryId}/deactivate`,
    );
  },
};
