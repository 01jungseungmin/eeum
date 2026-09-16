import { apiClient } from '../apiClient';

export const inquiryApi = {
  // 내 가게 문의 목록 조회 / 상세 조회
  getStoreInquiries: (params) => {
    return apiClient.get('/owner/stores/me/inquiries', { params });
  },
  getInquiryDetail: (inquiryId) => {
    return apiClient.get(`/owner/stores/me/inquiries/${inquiryId}`);
  },

  // 사장님 답변 등록
  createAnswer: (inquiryId, content) => {
    return apiClient.post(`/owner/stores/me/inquiries/${inquiryId}/answers`, {
      content,
    });
  },
};
