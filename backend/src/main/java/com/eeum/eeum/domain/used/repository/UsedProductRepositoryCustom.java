package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedProduct;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.Collection;

public interface UsedProductRepositoryCustom {

    // 동네 목록 조회
    Slice<UsedProduct> findByRegions(Collection<Long> regionIds, Pageable pageable);

    // 조회수 +1. 상세 조회마다 엔티티를 더럽히면 같은 행에 UPDATE가 몰리므로 원자 연산으로 처리
    void increaseViewCount(Long usedProductId);
}
