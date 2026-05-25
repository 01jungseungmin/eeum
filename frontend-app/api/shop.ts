import axios from 'axios';

const BASE_URL = process.env.EXPO_PUBLIC_API_URL;

const api = axios.create({
  baseURL: BASE_URL,
  timeout: 5000,
});

export const shopApi = {
  // 1. 상점 목록 가져오기
  getShops: async (category: string) => {
    return await api.get(`/shops`, { params: { category } });
  },
  
  // 2. 특정 상점 상세정보 가져오기
  getShopDetail: async (shopId: string) => {
    return await api.get(`/shops/${shopId}`);
  },

  // 3. 특정 상품 상세정보 가져오기
  getProductDetail: async (productId: string) => {
    return await api.get(`/products/${productId}`);
  }
};