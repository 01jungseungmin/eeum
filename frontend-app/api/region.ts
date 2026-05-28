import { client } from './client';

export const regionApi = {
  // 1. 내 활동 지역 목록 조회
  getMyRegions: async () => {
    const response = await client.get('/accounts/me/regions');
    return response.data;
  },

  // 2. 활동 지역 등록
  addRegion: async (regionId: number) => {
    const response = await client.post('/accounts/me/regions', { regionId });
    return response.data;
  },

  // 3. 대표 지역 설정
  setPrimaryRegion: async (accountRegionId: number) => {
    const response = await client.patch(`/accounts/me/regions/${accountRegionId}/primary`);
    return response.data;
  },

  // 4. 지역 GPS 인증
  verifyRegion: async (accountRegionId: number, latitude: number, longitude: number) => {
    const response = await client.patch(`/accounts/me/regions/${accountRegionId}/verify`, {
      latitude,
      longitude,
    });
    return response.data;
  },

  // 5. 지역 삭제
  deleteRegion: async (accountRegionId: number) => {
    const response = await client.delete(`/accounts/me/regions/${accountRegionId}`);
    return response.data;
  },

  // 6. 지역 키워드 검색 API
  searchRegion: async (keyword: string) => {
    try {
      const response = await client.get(`/regions/search?keyword=${encodeURIComponent(keyword)}`);
      return response.data; // { success: true, data: [...] } 구조가 넘어옵니다.
    } catch (error) {
      console.error('지역 검색 에러:', error);
      throw error;
    }
  },

  // 7. 현재 위치 기반 근처 지역 조회 API (추후 앱 고도화나 검증 자동화 시 켜기 용도)
  getNearbyRegions: async (latitude: number, longitude: number) => {
    try {
      const response = await client.get(`/regions/nearby?latitude=${latitude}&longitude=${longitude}`);
      return response.data;
    } catch (error) {
      console.error('근처 지역 조회 에러:', error);
      throw error;
    }
  }
};