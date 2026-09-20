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

/**
 * 수정 요청. regionId가 없는 것은 실수가 아니다 — 거래 희망 지역은 서버가 변경을 막는다.
 * 거래 희망 장소는 셋 다 보내거나 셋 다 null이어야 한다(부분 입력은 400).
 */
export interface UpdateUsedProductReq {
  categoryId?: number;
  title?: string;
  content?: string;
  priceType?: UsedProductPriceType;
  price?: number | null;
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
  /** 판매완료 시 확정된 거래 상대. 이 사람만 후기를 쓸 수 있다. */
  buyerAccountId: number | null;
  buyerNickname: string | null;
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
  },

  /** 작성자 본인만. 거래 희망 지역(regionId)은 서버가 변경을 막는다. */
  updateUsedProduct: async (
    usedProductId: number,
    data: UpdateUsedProductReq
  ): Promise<UsedProductDetail> => {
    const res = await client.patch(`/used/${usedProductId}`, data);
    return res.data?.data ?? res.data;
  },

  /** 작성자 본인만. 예약 중인 글은 예약을 먼저 취소해야 삭제된다. */
  deleteUsedProduct: async (usedProductId: number): Promise<void> => {
    await client.delete(`/used/${usedProductId}`);
  },

  // ===================== 거래 상태 =====================

  /** buyerId는 선택. 지정하면 예약 취소 시 함께 해제된다. */
  reserve: async (usedProductId: number, buyerId?: number | null): Promise<UsedProductDetail> => {
    const res = await client.post(
      `/used/${usedProductId}/reservation`,
      buyerId ? { buyerId } : undefined
    );
    return res.data?.data ?? res.data;
  },

  cancelReservation: async (usedProductId: number): Promise<UsedProductDetail> => {
    const res = await client.delete(`/used/${usedProductId}/reservation`);
    return res.data?.data ?? res.data;
  },

  /**
   * buyerId를 생략하면 예약 때 지정한 상대가 유지된다.
   * 여기서 확정된 상대만 후기를 쓸 수 있다.
   */
  markSold: async (usedProductId: number, buyerId?: number | null): Promise<UsedProductDetail> => {
    const res = await client.post(
      `/used/${usedProductId}/sold`,
      buyerId ? { buyerId } : undefined
    );
    return res.data?.data ?? res.data;
  },

  // ===================== 사진 =====================

  /** 대표 사진을 지우면 남은 첫 사진이 대표가 된다. */
  deleteImage: async (usedProductId: number, imageId: number): Promise<void> => {
    await client.delete(`/used/${usedProductId}/images/${imageId}`);
  },

  setThumbnail: async (usedProductId: number, imageId: number): Promise<void> => {
    await client.patch(`/used/${usedProductId}/images/${imageId}/thumbnail`);
  },
};
