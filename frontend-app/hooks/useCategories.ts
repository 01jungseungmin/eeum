import { useCallback, useEffect, useMemo, useState } from 'react';
import { Category, CategoryType, categoryApi } from '../api/category';

export type CategoryOption = { id: number; name: string };

/** 필터 목록 맨 앞에 붙는 '전체'. 서버에는 없는, 화면 전용 항목이다. */
export const ALL_CATEGORY_ID = 0;

/**
 * 카테고리를 API에서 받아온다.
 *
 * 예전에는 화면마다 상수 배열을 들고 있었는데, 운영 DB에는 상점 카테고리가 15개인
 * 반면 그 배열은 7개여서 뷰티/미용·농산물 같은 업종의 상점은 필터로 도달할 수가
 * 없었다. 카테고리는 관리자 화면에서 늘어나므로 코드에 박아두면 반드시 어긋난다.
 *
 * 마스터 데이터라 세션 동안 바뀌지 않는다. 화면마다 다시 받지 않도록 모듈 수준에
 * 캐시한다. 같은 타입을 동시에 요청하면 요청 하나를 공유한다.
 */
const cache = new Map<CategoryType, CategoryOption[]>();
const inFlight = new Map<CategoryType, Promise<CategoryOption[]>>();

async function loadCategories(type: CategoryType): Promise<CategoryOption[]> {
  const cached = cache.get(type);
  if (cached) return cached;

  const pending = inFlight.get(type);
  if (pending) return pending;

  const request = categoryApi
    .getCategories(type)
    .then((list: Category[]) => {
      const options = list.map((c) => ({ id: Number(c.categoryId), name: c.name }));
      cache.set(type, options);
      return options;
    })
    .finally(() => {
      inFlight.delete(type);
    });

  inFlight.set(type, request);
  return request;
}

type Options = {
  /** 목록 맨 앞에 '전체'를 넣을지. 필터 칩은 true, 글쓰기 선택은 false. */
  includeAll?: boolean;
};

export function useCategories(type: CategoryType, { includeAll = false }: Options = {}) {
  const [categories, setCategories] = useState<CategoryOption[]>(() => cache.get(type) ?? []);
  const [isLoading, setIsLoading] = useState(!cache.has(type));

  useEffect(() => {
    let alive = true;

    loadCategories(type)
      .then((list) => {
        if (alive) setCategories(list);
      })
      .catch((error) => {
        // 카테고리를 못 받아도 화면 자체는 떠야 한다. 목록이 비면 필터가 사라질 뿐
        // 상점·게시글 목록은 그대로 보인다.
        console.error('카테고리 조회 실패:', error);
      })
      .finally(() => {
        if (alive) setIsLoading(false);
      });

    return () => {
      alive = false;
    };
  }, [type]);

  // 매 렌더마다 새 배열을 만들면 이걸 의존성에 넣은 effect가 끝없이 다시 돈다.
  const options = useMemo(
    () => (includeAll ? [{ id: ALL_CATEGORY_ID, name: '전체' }, ...categories] : categories),
    [categories, includeAll]
  );

  /** id로 이름을 찾는다. 아직 못 받아왔거나 사라진 카테고리면 '기타'. */
  const getCategoryName = useCallback(
    (id: number | null | undefined) => categories.find((c) => c.id === id)?.name || '기타',
    [categories]
  );

  return { categories: options, getCategoryName, isLoading };
}
