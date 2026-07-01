import { client } from './client';

export const reviewApi = {
  // ==========================================
  // [1] 상점 리뷰 기본 CRUD
  // ==========================================

  // 1. 상점 리뷰 목록 조회 (GET /stores/{storeId}/reviews)
  getReviews: async (storeId: number, page: number = 0, size: number = 20) => {
    try {
      const response = await client.get(`/stores/${storeId}/reviews`, {
        params: { page, size }
      });
      return response.data?.data || response.data;
    } catch (error) {
      console.error('리뷰 목록 조회 에러:', error);
      throw error;
    }
  },

  // 2. 상점 리뷰 상세 조회 (GET /stores/{storeId}/reviews/{reviewId})
  getReviewDetail: async (storeId: number, reviewId: number) => {
    try {
      const response = await client.get(`/stores/${storeId}/reviews/${reviewId}`);
      return response.data?.data || response.data;
    } catch (error) {
      console.error('리뷰 상세 조회 에러:', error);
      throw error;
    }
  },

  // 3. 리뷰 작성 (POST /stores/{storeId}/reviews)
  createReview: async (storeId: number, data: { orderId?: number; reservationId?: number; rating: number; content: string; imageUrls?: string[] }) => {
    try {
      const response = await client.post(`/stores/${storeId}/reviews`, data);
      return response.data?.data || response.data;
    } catch (error) {
      console.error('리뷰 작성 에러:', error);
      throw error;
    }
  },

  // 4. 리뷰 수정 (PATCH /stores/{storeId}/reviews/{reviewId})
  updateReview: async (storeId: number, reviewId: number, data: { rating?: number; content?: string }) => {
    try {
      const response = await client.patch(`/stores/${storeId}/reviews/${reviewId}`, data);
      return response.data?.data || response.data;
    } catch (error) {
      console.error('리뷰 수정 에러:', error);
      throw error;
    }
  },

  // 5. 리뷰 삭제 (DELETE /stores/{storeId}/reviews/{reviewId})
  deleteReview: async (storeId: number, reviewId: number) => {
    try {
      const response = await client.delete(`/stores/${storeId}/reviews/${reviewId}`);
      return response.data;
    } catch (error) {
      console.error('리뷰 삭제 에러:', error);
      throw error;
    }
  },


  // ==========================================
  // [2] 리뷰 이미지 상세 관리
  // ==========================================

  // 6. 리뷰 이미지 추가 (POST /stores/{storeId}/reviews/{reviewId}/images)
  addReviewImages: async (storeId: number, reviewId: number, imageUrls: string[]) => {
    try {
      const response = await client.post(`/stores/${storeId}/reviews/${reviewId}/images`, { imageUrls });
      return response.data?.data || response.data;
    } catch (error) {
      console.error('리뷰 이미지 추가 에러:', error);
      throw error;
    }
  },

  // 7. 리뷰 이미지 삭제 (DELETE /stores/{storeId}/reviews/{reviewId}/images/{imageId})
  deleteReviewImage: async (storeId: number, reviewId: number, imageId: number) => {
    try {
      const response = await client.delete(`/stores/${storeId}/reviews/${reviewId}/images/${imageId}`);
      return response.data;
    } catch (error) {
      console.error('리뷰 이미지 삭제 에러:', error);
      throw error;
    }
  },

  // 8. 리뷰 썸네일 이미지 설정 (PATCH /stores/{storeId}/reviews/{reviewId}/images/{imageId}/thumbnail)
  setThumbnailImage: async (storeId: number, reviewId: number, imageId: number) => {
    try {
      const response = await client.patch(`/stores/${storeId}/reviews/${reviewId}/images/${imageId}/thumbnail`);
      return response.data?.data || response.data;
    } catch (error) {
      console.error('리뷰 썸네일 설정 에러:', error);
      throw error;
    }
  },


  // ==========================================
  // [3] 마이페이지 기능
  // ==========================================

  // 9. 내 리뷰 목록 조회 (GET /reviews/me)
  getMyReviews: async (type?: 'ORDER' | 'RESERVATION', page: number = 0, size: number = 20) => {
    try {
      const params: any = { page, size };
      if (type) params.type = type;

      const response = await client.get('/reviews/me', { params });
      return response.data?.data?.content || response.data?.data || [];
    } catch (error) {
      console.error('내 리뷰 목록 조회 에러:', error);
      throw error;
    }
  },

  // 예약 리뷰
  createReservationReview: async (reservationId: number, data: { rating: number; content: string; imageUrls?: string[] }) => {
    try {
      const response = await client.post(`/reservations/visits/${reservationId}/review`, data);
      return response.data?.data || response.data;
    } catch (error) {
      console.error('방문 예약 리뷰 작성 에러:', error);
      throw error;
    }
  }
};