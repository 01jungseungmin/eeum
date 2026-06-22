import { client } from './client';

export const communityApi = {
  // 1. 커뮤니티 게시글 (Post) API
  getPosts: async (page: number = 0, size: number = 20, categoryId?: number | null) => {
    const params: any = { page, size };
    
    if (categoryId) {
      params.categoryId = categoryId; 
    }

    const response = await client.get('/community/posts', { params });
    return response.data?.data?.content || []; 
  },
  
  getPostDetail: async (postId: string | number) => {
    const response = await client.get(`/community/posts/${postId}`);
    return response.data?.data || response.data;
  },

  createPost: async (data: { categoryId: number; title: string; content: string; imageUrls?: string[] }) => {
    const response = await client.post('/community/posts', data);
    return response.data;
  },

  updatePost: async (postId: string | number, data: { categoryId: number; title: string; content: string; imageUrls?: string[] }) => {
    const response = await client.patch(`/community/posts/${postId}`, data);
    return response.data;
  },

  deletePost: async (postId: string | number) => {
    const response = await client.delete(`/community/posts/${postId}`);
    return response.data;
  },

  // 2. 커뮤니티 댓글 및 대댓글 (Comment/Reply) API
  
  // 특정 게시글의 댓글 목록 조회
  getComments: async (postId: string | number) => {
    const response = await client.get(`/community/posts/${postId}/comments`);
    return response.data?.data?.content || response.data?.data || [];
  },

  // 댓글 작성
  createComment: async (postId: string | number, content: string) => {
    const response = await client.post(`/community/posts/${postId}/comments`, { content });
    return response.data;
  },

  // 특정 댓글의 대댓글 목록 조회
  getReplies: async (commentId: string | number) => {
    const response = await client.get(`/community/comments/${commentId}/replies`);
    return response.data?.data?.content || response.data?.data || [];
  },

  // 대댓글 작성
  createReply: async (commentId: string | number, content: string) => {
    const response = await client.post(`/community/comments/${commentId}/replies`, { content });
    return response.data;
  },

  // 댓글/대댓글 수정
  updateComment: async (commentId: string | number, content: string) => {
    const response = await client.patch(`/community/comments/${commentId}`, { content });
    return response.data;
  },

  // 댓글/대댓글 삭제
  deleteComment: async (commentId: string | number) => {
    const response = await client.delete(`/community/comments/${commentId}`);
    return response.data;
  },

  // 3. 커뮤니티 좋아요 (Like) API
  
  // 게시글 좋아요
  likePost: async (postId: string | number) => {
    const response = await client.post(`/community/posts/${postId}/likes`);
    return response.data;
  },

  // 게시글 좋아요 취소
  unlikePost: async (postId: string | number) => {
    const response = await client.delete(`/community/posts/${postId}/likes`);
    return response.data;
  },

  // 댓글/대댓글 좋아요
  likeComment: async (commentId: string | number) => {
    const response = await client.post(`/community/comments/${commentId}/likes`);
    return response.data;
  },

  // 댓글/대댓글 좋아요 취소
  unlikeComment: async (commentId: string | number) => {
    const response = await client.delete(`/community/comments/${commentId}/likes`);
    return response.data;
  }
};