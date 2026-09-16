import { client } from './client';

export type CategoryType = 'STORE' | 'USED' | 'COMMUNITY';

export interface Category {
  categoryId: number;
  parentId: number | null;
  name: string;
  displayOrder: number;
  depth: number;
}

export const categoryApi = {
  /** 타입별 활성 카테고리 목록. 비회원도 조회 가능. */
  getCategories: async (type: CategoryType): Promise<Category[]> => {
    const res = await client.get('/categories', { params: { type } });
    return res.data?.data ?? [];
  },
};
