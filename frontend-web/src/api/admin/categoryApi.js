import { apiClient } from '../apiClient';

export const adminCategoryApi = {
  // 공통 카테고리 목록 조회 / 생성 / 수정 / 삭제
  getCategories: (type) => {
    return apiClient.get('/admin/categories', {
      params: type ? { type } : {},
    });
  },
  createCategory: (data) => {
    return apiClient.post('/admin/categories', data);
  },
  updateCategory: (categoryId, data) => {
    return apiClient.patch(`/admin/categories/${categoryId}`, data);
  },
  deleteCategory: (categoryId) => {
    return apiClient.delete(`/admin/categories/${categoryId}`);
  },

  // 공통 카테고리 활성화 / 비활성화
  activateCategory: (categoryId) => {
    return apiClient.patch(`/admin/categories/${categoryId}/activate`);
  },
  deactivateCategory: (categoryId) => {
    return apiClient.patch(`/admin/categories/${categoryId}/deactivate`);
  },

  // 공통 카테고리 순서 일괄 변경
  updateCategoryOrder: (orderData) => {
    return apiClient.patch('/admin/categories/order', orderData);
  },
};
