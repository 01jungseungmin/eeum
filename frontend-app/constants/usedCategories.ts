/**
 * 중고거래 카테고리 (category 테이블의 type='USED' 행과 동일).
 *
 * 일반 사용자용 카테고리 조회 API가 아직 없어(관리자용 /admin/categories만 존재)
 * 상수로 둔다. 공개 API가 생기면 이 파일을 지우고 서버 조회로 바꾼다.
 */
export interface UsedCategory {
  id: number | null; // null이면 전체보기 (categoryId를 아예 보내지 않는다)
  name: string;
}

export const USED_CATEGORIES: UsedCategory[] = [
  { id: null, name: '전체' },
  { id: 13, name: '디지털/가전' },
  { id: 14, name: '의류/잡화' },
  { id: 15, name: '가구/인테리어' },
  { id: 16, name: '도서/음반' },
  { id: 17, name: '스포츠/레저' },
  { id: 18, name: '기타' },
];

/** 정렬 옵션. 백엔드는 createdAt·price·favoriteCount·viewCount만 지원한다. */
export type UsedSortKey = 'LATEST' | 'POPULAR' | 'PRICE_ASC' | 'PRICE_DESC';

export const USED_SORT_OPTIONS: { key: UsedSortKey; label: string; sort: string }[] = [
  { key: 'LATEST', label: '최신순', sort: 'createdAt,desc' },
  { key: 'POPULAR', label: '인기순', sort: 'favoriteCount,desc' },
  { key: 'PRICE_ASC', label: '낮은 가격순', sort: 'price,asc' },
  { key: 'PRICE_DESC', label: '높은 가격순', sort: 'price,desc' },
];
