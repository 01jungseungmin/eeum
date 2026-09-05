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
}

export interface UsedProductListParams {
  regionId?: number;
  keyword?: string;
  categoryId?: number | null;
  status?: UsedProductStatus[];
  /** Spring 정렬 표기. 예: 'createdAt,desc' */
  sort?: string;
  page?: number;
  size?: number;
}

export interface CreateUsedProductReq {
  categoryId: number;
  regionId: number;
  title: string;
  content: string;
  priceType: UsedProductPriceType;
  price: number;
}

export const usedApi = {
  // 동네 중고 게시글 목록 조회
  getUsedProducts: async (params: UsedProductListParams = {}) => {
    const { categoryId, ...rest } = params;

    return await client.get('/used', {
      // categoryId가 null이면(전체보기) 파라미터 자체를 빼야 한다.
      params: { ...rest, ...(categoryId ? { categoryId } : {}) },
      // status는 ?status=A&status=B 형태로 반복해야 서버 List 바인딩에 맞는다.
      paramsSerializer: { indexes: null },
    });
  },

  createUsedProduct: async (data: CreateUsedProductReq) => {
    return await client.post('/used', data);
  },

  uploadImages: async (usedProductId: number, data: { images: { imageUrl: string }[] }) => {
    return await client.post(`/used/${usedProductId}/images`, data);
  }
};
