import { apiClient } from '../apiClient';

export const reviewApi = {
  // 상점 리뷰 전체 조회 / 상세 조회
  getStoreReviews: (page = 0, size = 10) => {
    return apiClient.get(`/owner/stores/me/reviews`, {
      params: { page, size, sort: 'createdAt,desc' },
    });
  },
  getReviewDetail: function (reviewId) {
    return apiClient.get(`/owner/stores/me/reviews/${reviewId}`);
  },

  // 답글 등록 / 수정/ / 삭제
  createReply: (reviewId, content) => {
    return apiClient.post(`/owner/stores/me/reviews/${reviewId}/reply`, {
      content,
    });
  },
  updateReply: (reviewId, content) => {
    return apiClient.patch(`/owner/stores/me/reviews/${reviewId}/reply`, {
      content,
    });
  },
  deleteReply: (reviewId, replyId) => {
    return apiClient.delete(`/owner/stores/me/reviews/${reviewId}/reply`);
  },
};
