import { client } from './client';

export const inquiryApi = {
  // 1. 문의 작성 (POST /inquiries)
  createInquiry: async (data: { 
    targetType: string; 
    category: string; 
    storeId?: number; 
    title: string; 
    content: string; 
    secret: boolean; 
  }) => {
    try {
      const response = await client.post('/inquiries', data);
      return response.data?.data || response.data;
    } catch (error) {
      console.error('문의 작성 에러:', error);
      throw error;
    }
  },

  // 2. 내 문의 목록 조회 (GET /inquiries/me)
  getMyInquiries: async (page: number = 0, size: number = 20) => {
    try {
      const response = await client.get('/inquiries/me', {
        params: { page, size }
      });
      // 명세서 구조에 따라 data.content 배열을 반환
      return response.data?.data?.content || [];
    } catch (error) {
      console.error('내 문의 목록 조회 에러:', error);
      throw error;
    }
  },

  // 3. 내 문의 상세 조회 (GET /inquiries/{inquiryId})
  getInquiryDetail: async (inquiryId: number) => {
    try {
      const response = await client.get(`/inquiries/${inquiryId}`);
      // 명세서 구조에 따라 data 안의 상세 정보와 answers(답변)를 반환
      return response.data?.data || response.data;
    } catch (error) {
      console.error('문의 상세 조회 에러:', error);
      throw error;
    }
  }
};