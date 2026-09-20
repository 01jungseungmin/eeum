import { apiClient } from '../apiClient';

export const inquiryApi = {
  // 관리자 문의 목록 / 상세 조회
  // filters: { status: PENDING|ANSWERED|CLOSED, category, keyword } (생략하면 전체)
  getInquiryList: (page = 0, size = 10, filters = {}) => {
    return apiClient.get('/admin/inquiries', {
      params: { page, size, ...filters },
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

  // 문의 답변 수정
  updateAnswer: (inquiryId, answerId, content) => {
    return apiClient.patch(
      `/admin/inquiries/${inquiryId}/answers/${answerId}`,
      { content },
    );
  },

  // 문의 강제 종료 / 재오픈
  closeInquiry: (inquiryId) => {
    return apiClient.patch(`/admin/inquiries/${inquiryId}/close`);
  },
  reopenInquiry: (inquiryId) => {
    return apiClient.patch(`/admin/inquiries/${inquiryId}/reopen`);
  },
};
