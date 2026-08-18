package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedProduct;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface UsedProductRepositoryCustom {

    // 동네 목록 조회.
    // 활동 지역은 최대 2개지만 사용자는 그중 하나를 "선택한 동네"로 두고 사용
    Slice<UsedProduct> findByRegion(Long regionId, Pageable pageable);

    // 조회수 +1. 상세 조회마다 엔티티를 더럽히면 같은 행에 UPDATE가 몰리므로 원자 연산으로 처리
    void increaseViewCount(Long usedProductId);
}
