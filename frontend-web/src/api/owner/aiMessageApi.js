import { apiClient } from '../apiClient';

export const aiMessageApi = {
  // AI 생성 메시지 목록 조회 (type 필터 선택, Page 응답)
  getMessages: (page = 0, size = 20, type) => {
    return apiClient.get('/owner/ai-manager/generated-messages', {
      params: { page, size, ...(type && type !== 'ALL' ? { type } : {}) },
    });
  },

  // AI 생성 메시지 상세 조회
  getMessage: (messageId) => {
    return apiClient.get(`/owner/ai-manager/generated-messages/${messageId}`);
  },

  // AI 생성 메시지 수정 (초안/검토/예약 상태만 가능, 수정 시 REVIEWED 로 전환)
  updateMessage: (messageId, messageData) => {
    return apiClient.patch(
      `/owner/ai-manager/generated-messages/${messageId}`,
      messageData,
    );
  },

  // 검토 후 보내기
  sendMessage: (messageId) => {
    return apiClient.post(
      `/owner/ai-manager/generated-messages/${messageId}/send`,
    );
  },

  // 예약 발송
  scheduleMessage: (messageId, scheduledAt) => {
    return apiClient.post(
      `/owner/ai-manager/generated-messages/${messageId}/schedule`,
      { scheduledAt },
    );
  },

  // 메시지 취소 (발송 전만 가능)
  cancelMessage: (messageId) => {
    return apiClient.post(
      `/owner/ai-manager/generated-messages/${messageId}/cancel`,
    );
  },

  // 공지 등록 (NOTICE 유형 전용, SNS_CARD 채널은 발송 불가)
  publishNotice: (messageId) => {
    return apiClient.post(`/owner/ai-manager/notices/${messageId}/publish`);
  },

  // 공지 예약 등록
  scheduleNotice: (messageId, scheduledAt) => {
    return apiClient.post(`/owner/ai-manager/notices/${messageId}/schedule`, {
      scheduledAt,
    });
  },
};
