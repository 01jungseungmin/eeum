import { apiClient } from '../apiClient';

export const reservationApi = {
  // 방문 예약 목록 조회 / 상세 조회
  getVisitReservations: () => {
    return apiClient.get('/owner/reservations/visits');
  },
  getVisitReservationsDetail: (reservationId) => {
    return apiClient.get(
      '/owner/reservations/visits/{reservationId}',
      reservationId,
    );
  },

  // 방문 예약 승인 / 거절
  approveReservation: (reservationId) => {
    return apiClient.patch(
      `/owner/reservations/visits/${reservationId}/approve`,
    );
  },
  rejectReservation: (reservationId, rejectReason) => {
    return apiClient.patch(
      `/owner/reservations/visits/${reservationId}/reject`,
      {
        rejectReason: rejectReason,
      },
    );
  },

  // 테이블 구성 조회 / 요약 조회 / 저장
  getStoreTables: () => {
    return apiClient.get('/owner/reservations/visits/tables');
  },
  getTableSummary: () => {
    return apiClient.get('/owner/reservations/visits/tables/summary');
  },
  saveStoreTables: (tablesPayload) => {
    return apiClient.put('/owner/reservations/visits/tables', tablesPayload);
  },

  // 방문 예약 기본 설정 조회 / 수정
  getVisitSettings: () => {
    return apiClient.get('/owner/reservations/visits/settings');
  },
  updateVisitSettings: (settingsData) => {
    return apiClient.patch('/owner/reservations/visits/settings', settingsData);
  },

  // 특정 날짜 방문 예약 시간대 설정 조회 / 수정
  getDateTimeSlots: (date) => {
    return apiClient.get('/owner/reservations/visits/time-slots', {
      params: { date },
    });
  },
  saveDateTimeSlots: (timeSlotsData) => {
    return apiClient.put(
      '/owner/reservations/visits/time-slots',
      timeSlotsData,
    );
  },
};
