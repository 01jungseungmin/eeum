import axios from 'axios';

const api = axios.create({ baseURL: '서버주소' }); 

export const regionApi = {
  // 1. 내 활동 지역 목록 조회
  getMyRegions: async () => {
    const response = await api.get('/accounts/me/regions');
    return response.data;
  },

  // 2. 활동 지역 등록 (regionId 필요)
  addRegion: async (regionId: number) => {
    const response = await api.post('/accounts/me/regions', { regionId });
    return response.data;
  },

  // 3. 대표 지역 설정
  setPrimaryRegion: async (accountRegionId: number) => {
    const response = await api.patch(`/accounts/me/regions/${accountRegionId}/primary`);
    return response.data;
  },

  // 4. 지역 GPS 인증
  verifyRegion: async (accountRegionId: number, latitude: number, longitude: number) => {
    const response = await api.patch(`/accounts/me/regions/${accountRegionId}/verify`, {
      latitude,
      longitude,
    });
    return response.data;
  },

  // 5. 지역 삭제
  deleteRegion: async (accountRegionId: number) => {
    const response = await api.delete(`/accounts/me/regions/${accountRegionId}`);
    return response.data;
  }
};