import { client } from './client';

// 상점인지 중고거래인지 구분하는 타입
export type RefType = 'STORE' | 'USED_PRODUCT';

export const favoriteApi = {
  // 1. 찜 단건 조회 (상세 화면 들어갔을 때 하트 색칠 여부)
  checkFavorite: (refType: RefType, refId: number) =>
    client.get('/favorites/check', { params: { refType, refId } }),

  // 2. 대상별 찜 수 조회 (화면에 총 찜 개수 띄우기)
  getFavoriteCount: (refType: RefType, refId: number) =>
    client.get('/favorites/count', { params: { refType, refId } }),

  // 3. 찜 등록/해제 토글 (하트 버튼 눌렀을 때)
  toggleFavorite: (refType: RefType, refId: number) =>
    client.post('/favorites', { refType, refId }),
  
  // 4. 내 찜 목록 전체 조회 (추가된 코드!)
  getMyFavorites: (refType: RefType) =>
    client.get(`/favorites/me/${refType}`),
};