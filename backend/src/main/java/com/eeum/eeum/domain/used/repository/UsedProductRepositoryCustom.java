package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedProduct;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface UsedProductRepositoryCustom {

    // 동네 목록 조회.
    // 활동 지역은 최대 2개지만 사용자는 그중 하나를 "선택한 동네"로 두고 사용
    Slice<UsedProduct> search(UsedProductSearchCondition condition, Pageable pageable);

    // 조회수 +1. 상세 조회마다 엔티티를 더럽히면 같은 행에 UPDATE가 몰리므로 원자 연산으로 처리
    // 노출 대상일 때만 조회수를 올린다. 공개 여부 확인과 증가 사이에 숨김·삭제가 커밋될 수 있다.
    // 반환값은 갱신된 행 수 — 0이면 그 사이 비공개로 바뀐 것이다.
    long increaseViewCount(Long usedProductId);
}
