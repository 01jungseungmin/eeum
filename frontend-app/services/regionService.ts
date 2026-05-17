import { client } from '../api/client'; // client.js의 경로에 맞게 점(..) 개수를 조절해 주세요.

export const regionService = {
  // 1. 활동 지역 목록 조회 (GET)
  getRegions: async () => {
    const response = await client.get('/accounts/me/regions');
    return response.data; 
  },

  // 2. 활동 지역 등록 (POST)
  // 스웨거 명세에 따라 regionId를 Body에 담아 보냅니다.
  addRegion: async (regionId: number) => {
    const response = await client.post('/accounts/me/regions', { regionId });
    return response.data;
  },

  // 3. 활동 지역 GPS 인증 (PATCH)
  // 위도(latitude), 경도(longitude)를 보내 현재 위치가 맞는지 인증합니다.
  verifyRegion: async (accountRegionId: number, latitude: number, longitude: number) => {
    const response = await client.patch(`/accounts/me/regions/${accountRegionId}/verify`, {
      latitude,
      longitude
    });
    return response.data;
  },

  // 4. 대표 지역 설정 (PATCH)
  setPrimaryRegion: async (accountRegionId: number) => {
    const response = await client.patch(`/accounts/me/regions/${accountRegionId}/primary`);
    return response.data;
  },

  // 5. 활동 지역 삭제 (DELETE)
  deleteRegion: async (accountRegionId: number) => {
    const response = await client.delete(`/accounts/me/regions/${accountRegionId}`);
    return response.data;
  }
};