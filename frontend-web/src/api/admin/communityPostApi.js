import { apiClient } from '../apiClient';

// 관리자 커뮤니티 게시글 관리 API (AdminCommunityPostController)
export const communityPostApi = {
  // 게시글 목록 조회 (숨김 포함, 최신순)
  getPosts: (params) => apiClient.get('/admin/community/posts', { params }),

  // 게시글 상세 조회
  getPostDetail: (postId) => apiClient.get(`/admin/community/posts/${postId}`),

  // 게시글 숨김 / 숨김 해제
  hidePost: (postId) =>
    apiClient.patch(`/admin/community/posts/${postId}/hide`),
  showPost: (postId) =>
    apiClient.patch(`/admin/community/posts/${postId}/show`),
};
