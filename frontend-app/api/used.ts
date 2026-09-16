import { client } from './client';

export type UsedProductPriceType = 'FIXED' | 'NEGOTIABLE' | 'FREE';
export type UsedProductStatus = 'SELLING' | 'RESERVED' | 'SOLD';

export interface UsedProductSummary {
  usedProductId: number;
  title: string;
  priceType: UsedProductPriceType;
  price: number | null;
  status: UsedProductStatus;
  regionName: string | null;
  categoryName: string | null;
  thumbnailUrl: string | null;
  favoriteCount: number;
  createdAt: string;
  tradeLocationName: string | null;
}

export interface UsedProductListParams {
  regionId?: number;
  keyword?: string;
  categoryId?: number | null;
  status?: UsedProductStatus[];
  /** Spring 정렬 표기. 예: 'createdAt,desc' */
  sort?: string;
  size?: number;
  /** 커서 페이징. 둘 다 있어야 다음 페이지로 취급되고, 없으면 첫 페이지. */
  cursorValue?: string | null;
  cursorId?: number | null;
}

/** 백엔드 CursorSlice<T> 응답 공통 형태 */
export interface UsedProductSlice {
  content: UsedProductSummary[];
  hasNext: boolean;
  nextCursorValue: string | null;
  nextCursorId: number | null;
}

export interface CreateUsedProductReq {
  categoryId: number;
  regionId: number;
  title: string;
  content: string;
  priceType: UsedProductPriceType;
  price: number;
  // 거래 희망 장소(선택). 셋 다 있거나 셋 다 없어야 한다 — 서버가 부분 입력을 400으로 막는다.
  tradeLocationName?: string | null;
  tradeLatitude?: number | null;
  tradeLongitude?: number | null;
  tradePlaceId?: string | null;
}

export interface UsedProductImage {
  imageId: number;
  imageUrl: string;
  displayOrder: number;
  thumbnail: boolean;
}

export interface UsedProductDetail {
  usedProductId: number;
  sellerId: number;
  sellerNickname: string | null;
  categoryId: number | null;
  categoryName: string | null;
  regionId: number | null;
  regionName: string | null;
  title: string;
  content: string;
  priceType: UsedProductPriceType;
  price: number | null;
  status: UsedProductStatus;
  hidden: boolean;
  viewCount: number;
  favoriteCount: number;
  createdAt: string;
  modifiedAt: string;
  images: UsedProductImage[];
  tradeLocationName: string | null;
  tradeLatitude: number | null;
  tradeLongitude: number | null;
  tradePlaceId: string | null;
}

export const usedApi = {
  // 동네 중고 게시글 목록 조회 (cursorValue+cursorId 기반 커서 페이징)
  getUsedProducts: async (params: UsedProductListParams = {}): Promise<UsedProductSlice> => {
    const { categoryId, cursorValue, cursorId, ...rest } = params;

    const query: any = {
      ...rest,
      // categoryId가 null이면(전체보기) 파라미터 자체를 빼야 한다.
      ...(categoryId ? { categoryId } : {}),
    };
    // cursorValue/cursorId는 둘 다 있어야 하고, 없으면 아예 빼야 첫 페이지로 취급된다.
    if (cursorValue != null && cursorId != null) {
      query.cursorValue = cursorValue;
      query.cursorId = cursorId;
    }

    const response = await client.get('/used', {
      params: query,
      // status는 ?status=A&status=B 형태로 반복해야 서버 List 바인딩에 맞는다.
      paramsSerializer: { indexes: null },
    });

    const raw = response.data?.data;
    return {
      content: raw?.content ?? [],
      hasNext: raw?.hasNext ?? false,
      nextCursorValue: raw?.nextCursorValue ?? null,
      nextCursorId: raw?.nextCursorId ?? null,
    };
  },

  // 중고 게시글 상세 조회 (비회원도 조회 가능, 본인 글이 아니면 조회수가 오른다)
  getUsedProduct: async (usedProductId: number): Promise<UsedProductDetail> => {
    const res = await client.get(`/used/${usedProductId}`);
    return res.data.data;
  },

  createUsedProduct: async (data: CreateUsedProductReq) => {
    return await client.post('/used', data);
  },

  uploadImages: async (usedProductId: number, data: { images: { imageUrl: string }[] }) => {
    return await client.post(`/used/${usedProductId}/images`, data);
  }
};
