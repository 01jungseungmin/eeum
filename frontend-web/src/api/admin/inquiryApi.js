import { apiClient } from '../apiClient';

export const inquiryApi = {
  // 관리자 문의 목록 / 상세 조회
  getInquiryList: (page = 0, size = 10) => {
    return apiClient.get('/admin/inquiries', {
      params: { page, size },
    });
  },
  getInquiryDetail: (inquiryId) => {
    return apiClient.get(`/admin/inquiries/${inquiryId}`);
  },

  // 문의 답변 등록
  createAnswer: (inquiryId, content) => {
    return apiClient.post(`/admin/inquiries/${inquiryId}/answers`, {
      content: content,
    });
  },
};
