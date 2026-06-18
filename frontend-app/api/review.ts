import { client } from './client';

export const reviewApi = {
  // 1. 상점 리뷰 목록 조회
  getReviews: async (storeId: number) => {
    try {
      const response = await client.get(`/stores/${storeId}/reviews`);
      // 데이터 구조: response.data.data.content
      return response.data?.data || response.data;
    } catch (error) {
      console.error('리뷰 목록 조회 에러:', error);
      throw error;
    }
  },

  // 2. 리뷰 작성 (orderId와 imageUrls 포함)
  createReview: async (storeId: number, data: { orderId: number; rating: number; content: string; imageUrls: string[] }) => {
    try {
      const response = await client.post(`/stores/${storeId}/reviews`, data);
      return response.data;
    } catch (error) {
      console.error('리뷰 작성 에러:', error);
      throw error;
    }
  },

  // (참고) 리뷰에 이미지 추가 API - 지금은 createReview에서 한 번에 보내므로 당장 안 써도 됩니다.
  addReviewImages: async (storeId: number, reviewId: number, imageUrls: string[]) => {
    try {
      const response = await client.post(`/stores/${storeId}/reviews/${reviewId}/images`, { imageUrls });
      return response.data;
    } catch (error) {
      console.error('리뷰 이미지 추가 에러:', error);
      throw error;
    }
  }
};