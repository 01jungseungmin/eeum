import { client } from './client';

export interface UsedReview {
  usedReviewId: number;
  usedProductId: number;
  usedProductTitle: string;
  /** 글이 삭제·숨김되면 false. 제목을 가려야 한다. */
  usedProductVisible: boolean;
  reviewerAccountId: number;
  reviewerNickname: string | null;
  rating: number;
  content: string;
  createdAt: string;
  modifiedAt: string;
}

export interface UsedReviewSummary {
  sellerId: number;
  reviewCount: number;
  /** 후기가 없으면 null */
  averageRating: number | null;
}

export interface UsedReviewSlice {
  content: UsedReview[];
  hasNext: boolean;
  nextCursorValue: string | null;
  nextCursorId: number | null;
}

/** 커서는 둘 다 있어야 다음 페이지로 취급된다. 하나만 보내면 400. */
export interface UsedReviewCursorParams {
  cursorValue?: string | null;
  cursorId?: number | null;
  size?: number;
}

const toSlice = (raw: any): UsedReviewSlice => ({
  content: raw?.content ?? [],
  hasNext: raw?.hasNext ?? false,
  nextCursorValue: raw?.nextCursorValue ?? null,
  nextCursorId: raw?.nextCursorId ?? null,
});

const toCursorQuery = ({ cursorValue, cursorId, size = 20 }: UsedReviewCursorParams) => {
  const query: any = { size };
  if (cursorValue != null && cursorId != null) {
    query.cursorValue = cursorValue;
    query.cursorId = cursorId;
  }
  return query;
};

export const usedReviewApi = {
  /** 판매완료(SOLD)된 거래에서 확정된 구매자만 쓸 수 있다. */
  createReview: async (
    usedProductId: number,
    data: { rating: number; content: string }
  ): Promise<UsedReview> => {
    const res = await client.post(`/used/${usedProductId}/reviews`, data);
    return res.data?.data ?? res.data;
  },

  /** 판매자가 받은 후기. 비회원도 조회 가능. */
  getSellerReviews: async (
    sellerId: number,
    params: UsedReviewCursorParams = {}
  ): Promise<UsedReviewSlice> => {
    const res = await client.get(`/used/sellers/${sellerId}/reviews`, {
      params: toCursorQuery(params),
    });
    return toSlice(res.data?.data);
  },

  /** 평점 요약. 비회원도 조회 가능. */
  getSellerReviewSummary: async (sellerId: number): Promise<UsedReviewSummary> => {
    const res = await client.get(`/used/sellers/${sellerId}/reviews/summary`);
    return res.data?.data ?? res.data;
  },

  getMyReviews: async (params: UsedReviewCursorParams = {}): Promise<UsedReviewSlice> => {
    const res = await client.get('/used/reviews/me', { params: toCursorQuery(params) });
    return toSlice(res.data?.data);
  },

  updateReview: async (
    usedReviewId: number,
    data: { rating: number; content: string }
  ): Promise<UsedReview> => {
    const res = await client.patch(`/used/reviews/${usedReviewId}`, data);
    return res.data?.data ?? res.data;
  },

  deleteReview: async (usedReviewId: number): Promise<void> => {
    await client.delete(`/used/reviews/${usedReviewId}`);
  },
};
