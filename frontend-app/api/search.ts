import { client } from './client';

export type PopularSearchScope = 'ALL' | 'STORE' | 'USED' | 'COMMUNITY';

export interface PopularKeyword {
  rank: number;
  keyword: string;
}

export const searchApi = {
  /**
   * 인기 검색어. 최근 24시간 기준이라 아무도 검색하지 않았으면 빈 배열이 온다.
   * 비회원도 조회 가능.
   */
  getPopularKeywords: async (
    scope: PopularSearchScope = 'ALL',
    limit: number = 10
  ): Promise<PopularKeyword[]> => {
    try {
      const res = await client.get('/searches/popular-keywords', { params: { scope, limit } });
      return res.data?.data ?? [];
    } catch (error) {
      // 인기 검색어는 검색 화면의 곁다리다. 실패해도 검색 자체는 되어야 한다.
      console.error('인기 검색어 조회 실패:', error);
      return [];
    }
  },
};
