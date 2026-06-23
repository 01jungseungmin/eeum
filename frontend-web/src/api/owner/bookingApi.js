import { apiClient } from '../apiClient';

export const bookingApi = {
  // 방문 예약 기본 설정 조회
  getVisitSettings: () => {
    return apiClient.get('/owner/reservations/visits/settings');
  },

  getVisitTimeSlots: (date) => {
    return apiClient.get('/owner/reservations/visits/time-slots', {
      params: { date },
    });
  },
};
