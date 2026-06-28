import { client } from './client';

export const communityApi = {
  // 1. 커뮤니티 게시글 (Post) API
  getPosts: async (page: number = 0, size: number = 20, keyword?: string) => {
    try {
      const response = await client.get('/community/posts', {
        params: { 
          page, 
          size, 
          keyword: keyword || undefined 
        }
      });
      return response.data?.data?.content || [];
    } catch (error) {
      console.error('게시글 목록 조회 에러:', error);
      throw error;
    }
  },
  
  getPostDetail: async (postId: string | number) => {
    const response = await client.get(`/community/posts/${postId}`);
    return response.data?.data || response.data;
  },

  // ✨ imageUrls 제거 (이제 텍스트 데이터만 먼저 보냅니다)
  createPost: async (data: { categoryId: number; title: string; content: string }) => {
    const response = await client.post('/community/posts', data);
    return response.data;
  },

  updatePost: async (postId: string | number, data: { categoryId: number; title: string; content: string }) => {
    const response = await client.patch(`/community/posts/${postId}`, data);
    return response.data;
  },

  deletePost: async (postId: string | number) => {
    const response = await client.delete(`/community/posts/${postId}`);
    return response.data;
  },

  uploadPostImages: async (postId: string | number, images: { imageUrl: string; thumbnail: boolean }[]) => {
    const response = await client.post(`/community/posts/${postId}/images`, { images });
    return response.data;
  },

  deletePostImage: async (postId: string | number, imageId: string | number) => {
    const response = await client.delete(`/community/posts/${postId}/images/${imageId}`);
    return response.data;
  },

  // =========================================================
  // 2. 커뮤니티 댓글 및 대댓글 (Comment/Reply) API
  // =========================================================
  getComments: async (postId: string | number) => {
    const response = await client.get(`/community/posts/${postId}/comments`);
    return response.data?.data?.content || response.data?.data || [];
  },

  createComment: async (postId: string | number, content: string) => {
    const response = await client.post(`/community/posts/${postId}/comments`, { content });
    return response.data;
  },

  getReplies: async (commentId: string | number) => {
    const response = await client.get(`/community/comments/${commentId}/replies`);
    return response.data?.data?.content || response.data?.data || [];
  },

  createReply: async (commentId: string | number, content: string) => {
    const response = await client.post(`/community/comments/${commentId}/replies`, { content });
    return response.data;
  },

  updateComment: async (commentId: string | number, content: string) => {
    const response = await client.patch(`/community/comments/${commentId}`, { content });
    return response.data;
  },

  deleteComment: async (commentId: string | number) => {
    const response = await client.delete(`/community/comments/${commentId}`);
    return response.data;
  },

  // =========================================================
  // 3. 커뮤니티 좋아요 (Like) API
  // =========================================================
  likePost: async (postId: string | number) => {
    const response = await client.post(`/community/posts/${postId}/likes`);
    return response.data;
  },

  unlikePost: async (postId: string | number) => {
    const response = await client.delete(`/community/posts/${postId}/likes`);
    return response.data;
  },

  likeComment: async (commentId: string | number) => {
    const response = await client.post(`/community/comments/${commentId}/likes`);
    return response.data;
  },

  unlikeComment: async (commentId: string | number) => {
    const response = await client.delete(`/community/comments/${commentId}/likes`);
    return response.data;
  },

  getMyPosts: async (page: number = 0, size: number = 20) => {
    try {
      const response = await client.get('/community/posts/me', {
        params: { page, size }
      });
      return response.data?.data?.content || [];
    } catch (error) {
      console.error('내 게시글 목록 조회 에러:', error);
      throw error;
    }
  },

  getMyComments: async (page: number = 0, size: number = 20) => {
    try {
      const response = await client.get('/community/comments/me', {
        params: { page, size }
      });
      return response.data?.data?.content || [];
    } catch (error) {
      console.error('내 댓글 목록 조회 에러:', error);
      throw error;
    }
  },
};