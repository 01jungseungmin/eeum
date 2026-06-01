import { client } from './client';

export const shopApi = {
  // 상점 목록 조회
  getShops: async (params?: { categoryId?: number; regionId?: number; keyword?: string; page?: number; size?: number }) => {
    try {
      const response = await client.get('/stores', { params });
      return response.data; 
    } catch (error) {
      console.error('상점 목록 조회 에러:', error);
      throw error;
    }
  },

  // 상점 상세 조회 (GET /stores/{storeId})
  getShopDetail: async (storeId: number) => {
    try {
      const response = await client.get(`/stores/${storeId}`);
      return response.data?.data || response.data; 
    } catch (error) {
      console.error('상점 상세 조회 에러:', error);
      throw error;
    }
  },

  // 상점 상품(메뉴) 목록 조회 (GET /stores/{storeId}/products)
  getShopProducts: async (storeId: number) => {
    try {
      const response = await client.get(`/stores/${storeId}/products`);
      return response.data?.data || response.data; 
    } catch (error) {
      console.error('상점 상품 조회 에러:', error);
      throw error;
    }
  },

  // 상품 상세 조회 (GET /products/{productId})
  getProductDetail: async (productId: number) => {
    try {
      const response = await client.get(`/products/${productId}`);
      return response.data?.data || response.data; 
    } catch (error) {
      console.error('상품 상세 조회 에러:', error);
      throw error;
    }
  },

  // 상품 옵션 조회 (GET /products/{productId}/options)
  getProductOptions: async (productId: number) => {
    try {
      const response = await client.get(`/products/${productId}/options`);
      return response.data?.data || response.data; 
    } catch (error) {
      console.error('상품 옵션 조회 에러:', error);
      throw error;
    }
  }
};