import { apiClient } from '../apiClient';

export const productApi = {
  // 사장 상품 목록 조회
  getOwnerProducts: () => apiClient.get('/owner/products'),
  // 사장 상품 상세 조회
  getOwnerProductDetail: (productId) => {
    return apiClient.get(`/owner/products/${productId}`);
  },

  // 사장 상품 등록 / 수정 / 삭제
  createProduct: (productData) => {
    return apiClient.post('/owner/products', productData);
  },
  updateOwnerProduct: (productId, data) => {
    return apiClient.patch(`/owner/products/${productId}`, data);
  },
  deleteOwnerProduct: (productId) => {
    return apiClient.delete(`/owner/products/${productId}`);
  },

  // 사장 상품 이미지 조회 / 등록 / 삭제
  getProductImages: (productId) => {
    return apiClient.get(`/owner/products/${productId}/images`);
  },
  registerProductImages: (productId, data) => {
    return apiClient.post(`/owner/products/${productId}/images`, data);
  },
  deleteOwnerProductImage: (productId, imageId) => {
    return apiClient.delete(`/owner/products/${productId}/images/${imageId}`);
  },

  // 사장 상품 대표 이미지 설정
  setProductMainImage: (productId, imageId) => {
    return apiClient.patch(
      `/owner/products/${productId}/images/${imageId}/thumbnail`,
    );
  },
};
