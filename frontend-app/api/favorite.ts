import { client } from './client';

// 상점인지 중고거래인지 구분하는 타입
export type RefType = 'STORE' | 'USED_PRODUCT';

export type UsedProductPriceType = 'FIXED' | 'NEGOTIABLE' | 'FREE';
export type UsedProductStatus = 'SELLING' | 'RESERVED' | 'SOLD';

/** 찜 여부 조회 응답. favoriteId는 단건 조회에서 찜한 경우에만 채워진다(배치는 항상 null). */
export interface FavoriteCheck {
  refType: RefType;
  refId: number;
  favorited: boolean;
  favoriteId: number | null;
}

/** 찜 토글 응답. favoriteCount는 비공개 대상 해제 시 null이 올 수 있다. */
export interface FavoriteToggle {
  favorited: boolean;
  favoriteId: number | null;
  refType: RefType;
  refId: number;
  favoriteCount: number | null;
  createdAt: string | null;
}

export interface FavoriteStore {
  favoriteId: number;
  storeId: number;
  name: string;
  address: string;
  rating: number | null;
  reviewCount: number | null;
  status: string;
  thumbnailUrl: string | null;
  favoritedAt: string;
}

export interface FavoriteUsedProduct {
  favoriteId: number;
  usedProductId: number;
  title: string;
  priceType: UsedProductPriceType;
  price: number | null;
  status: UsedProductStatus;
  regionName: string | null;
  thumbnailUrl: string | null;
  favoritedAt: string;
}

/** refType 정보만 담은 경량 응답 (전체 목록용) */
export interface FavoriteRef {
  favoriteId: number;
  refType: RefType;
  refId: number;
  createdAt: string;
}

/** 무한 스크롤용 페이지. 서버가 Slice를 주므로 totalElements는 없다. */
export interface FavoritePage<T> {
  content: T[];
  page: number;
  size: number;
  hasNext: boolean;
}

export interface PageParams {
  page?: number;
  size?: number;
}

const DEFAULT_SIZE = 20;

/**
 * Spring Slice 응답을 화면에서 쓰기 좋은 형태로 정리한다.
 *
 * Slice.hasNext()는 Jackson 기본 규칙(get/is 접두사)에 걸리지 않아 JSON에 실리지 않는다.
 * 대신 직렬화되는 `last`를 뒤집어 쓰고, 그마저 없으면 받아온 개수로 판단한다.
 */
const toPage = <T>(raw: any, requestedSize: number): FavoritePage<T> => {
  const content: T[] = raw?.content ?? [];
  const size = raw?.size ?? requestedSize;
  const hasNext =
    typeof raw?.last === 'boolean' ? !raw.last : content.length >= size;

  return {
    content,
    page: raw?.number ?? 0,
    size,
    hasNext,
  };
};

export const favoriteApi = {
  // ===================== 찜 여부 / 개수 =====================

  /** 찜 단건 조회 (상세 화면 들어갔을 때 하트 색칠 여부) */
  checkFavorite: async (refType: RefType, refId: number): Promise<FavoriteCheck> => {
    const res = await client.get('/favorites/check', { params: { refType, refId } });
    return res.data.data;
  },

  /**
   * 찜 여부 배치 조회 (목록 화면에서 N+1 없이 한 번에).
   * 서버 제한이 100개라 그 단위로 끊어 보낸 뒤 합친다.
   */
  checkFavoritesBatch: async (
    refType: RefType,
    refIds: number[]
  ): Promise<FavoriteCheck[]> => {
    if (refIds.length === 0) return [];

    const CHUNK_SIZE = 100;
    const chunks: number[][] = [];
    for (let i = 0; i < refIds.length; i += CHUNK_SIZE) {
      chunks.push(refIds.slice(i, i + CHUNK_SIZE));
    }

    const results = await Promise.all(
      chunks.map((chunk) =>
        client.post('/favorites/check/batch', { refType, refIds: chunk })
      )
    );
    return results.flatMap((res) => res.data.data ?? []);
  },

  /** 대상별 찜 수 조회 (비회원도 호출 가능) */
  getFavoriteCount: async (refType: RefType, refId: number): Promise<number> => {
    const res = await client.get('/favorites/count', { params: { refType, refId } });
    return res.data.data ?? 0;
  },

  // ===================== 등록 / 해제 =====================

  /** 찜 등록/해제 토글 (하트 버튼) */
  toggleFavorite: async (refType: RefType, refId: number): Promise<FavoriteToggle> => {
    const res = await client.post('/favorites', { refType, refId });
    return res.data.data;
  },

  /** 찜 삭제 (favoriteId를 이미 아는 내 찜 목록용) */
  deleteFavorite: async (favoriteId: number): Promise<void> => {
    await client.delete(`/favorites/${favoriteId}`);
  },

  /** 찜 삭제 (상세/목록 화면용. check 호출 없이 바로 해제) */
  deleteFavoriteByRef: async (refType: RefType, refId: number): Promise<void> => {
    await client.delete('/favorites', { params: { refType, refId } });
  },

  // ===================== 내 찜 목록 =====================

  /** 내 찜 전체 목록 (refType/refId만 담긴 경량 응답) */
  getMyFavorites: async ({
    page = 0,
    size = DEFAULT_SIZE,
  }: PageParams = {}): Promise<FavoritePage<FavoriteRef>> => {
    const res = await client.get('/favorites/me/all', { params: { page, size } });
    return toPage<FavoriteRef>(res.data.data, size);
  },

  /** 상점 찜 목록 (평점·썸네일 등 상점 정보 포함) */
  getMyFavoriteStores: async ({
    page = 0,
    size = DEFAULT_SIZE,
  }: PageParams = {}): Promise<FavoritePage<FavoriteStore>> => {
    const res = await client.get('/favorites/me/store', { params: { page, size } });
    return toPage<FavoriteStore>(res.data.data, size);
  },

  /** 중고 게시글 찜 목록 (관리자가 숨긴 글은 서버에서 제외된다) */
  getMyFavoriteUsedProducts: async ({
    page = 0,
    size = DEFAULT_SIZE,
  }: PageParams = {}): Promise<FavoritePage<FavoriteUsedProduct>> => {
    const res = await client.get('/favorites/me/used', { params: { page, size } });
    return toPage<FavoriteUsedProduct>(res.data.data, size);
  },
};
