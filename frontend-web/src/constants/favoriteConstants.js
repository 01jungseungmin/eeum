// 관리자 찜 통계 도메인 상수 (domain/favorite/enums/FavoriteRefType.java)
export const FAVORITE_REF_TYPE_LABEL = {
  STORE: '상점',
  USED_PRODUCT: '중고거래 게시글',
};

export const FAVORITE_REF_TYPE_TABS = [
  { value: 'STORE', label: '상점' },
  { value: 'USED_PRODUCT', label: '중고거래 게시글' },
];

// 재계산 대상 선택지 — 전체(refType 미전달) 옵션 포함
export const FAVORITE_RECALCULATE_OPTIONS = [
  { value: '', label: '전체' },
  { value: 'STORE', label: '상점만' },
  { value: 'USED_PRODUCT', label: '중고거래 게시글만' },
];

export const FAVORITE_STAT_LIMIT_OPTIONS = [10, 20, 50, 100];
