package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedProduct;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Slice;

public interface UsedProductRepositoryCustom {

    /**
     * 동네 목록 조회.
     *
     * <p>활동 지역은 최대 2개지만 사용자는 그중 하나를 "선택한 동네"로 두고 사용한다.
     *
     * @param cursor        직전 페이지의 마지막 글. 첫 페이지면 null이다.
     * @param size          한 페이지 크기. 다음 페이지 여부 판정을 위해 내부적으로 한 건 더 읽는다.
     * @param requestedSort 요청 정렬. 허용 필드 하나만 반영하고 tie-break를 붙인다.
     */
    Slice<UsedProduct> search(
            UsedProductSearchCondition condition, UsedProductCursor cursor, int size, Sort requestedSort);

    // 조회수 +1. 상세 조회마다 엔티티를 더럽히면 같은 행에 UPDATE가 몰리므로 원자 연산으로 처리
    // 노출 대상일 때만 조회수를 올린다. 공개 여부 확인과 증가 사이에 숨김·삭제가 커밋될 수 있다.
    // 반환값은 갱신된 행 수 — 0이면 그 사이 비공개로 바뀐 것이다.
    long increaseViewCount(Long usedProductId);
}
