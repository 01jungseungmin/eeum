// 💡 axios 라이브러리를 직접 부르지 않고, 우리가 설정해둔 client만 불러옵니다.
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

  // ✨ 6. 지역 검색 (추가된 부분)
  searchRegion: async (keyword: string) => {
    try {
      const response = await client.get(`/regions?search=${keyword}`);
      return response.data;
    } catch (error) {
      console.error('지역 검색 에러:', error);
      throw error; // 💡 프론트엔드 화면(컴포넌트)에서 에러 팝업을 띄울 수 있도록 에러를 던져줍니다.
    }
  }
};