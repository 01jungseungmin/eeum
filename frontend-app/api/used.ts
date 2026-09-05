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
