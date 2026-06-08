import { client } from './client';

export const reservationApi = {
  // 1. 매장 방문 예약 생성 (POST /reservations/visits/stores/{storeId})
  createVisitReservation: async (
    storeId: number,
    data: { visitDate: string; visitTime: string; visitorCount: number; requestMessage?: string }
  ) => {
    try {
      const response = await client.post(`/reservations/visits/stores/${storeId}`, data);
      return response.data;
    } catch (error) {
      console.error('방문 예약 생성 에러:', error);
      throw error;
    }
  },

  // 2. 내 방문 예약 취소 (PATCH /reservations/visits/{reservationId}/cancel)
  cancelVisitReservation: async (reservationId: number) => {
    try {
      const response = await client.patch(`/reservations/visits/${reservationId}/cancel`);
      return response.data;
    } catch (error) {
      console.error('방문 예약 취소 에러:', error);
      throw error;
    }
  },

  // 3. 내 방문 예약 목록 조회 (GET /reservations/visits)
  getMyVisitReservations: async (params?: { page?: number; size?: number; sort?: string }) => {
    try {
      const response = await client.get('/reservations/visits', { params });
      return response.data?.data || response.data;
    } catch (error) {
      console.error('방문 예약 목록 조회 에러:', error);
      throw error;
    }
  },

  // 4. 내 방문 예약 상세 조회 (GET /reservations/visits/{reservationId})
  getVisitReservationDetail: async (reservationId: number) => {
    try {
      const response = await client.get(`/reservations/visits/${reservationId}`);
      return response.data?.data || response.data;
    } catch (error) {
      console.error('방문 예약 상세 조회 에러:', error);
      throw error;
    }
  }
};