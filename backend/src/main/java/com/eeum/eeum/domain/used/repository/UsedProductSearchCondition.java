package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * 중고 게시글 검색 조건
 *
 * @param regionId  조회할 동네. 유일한 필수 조건이다.
 * @param keyword   제목·본문 검색어
 * @param categoryIds 카테고리 ID 목록. 상위 카테고리를 고르면 하위 전체가 펼쳐져 들어온다.
 * @param priceType 거래 유형(정가/나눔/가격제안)
 * @param minPrice  최소 가격
 * @param maxPrice  최대 가격
 * @param statuses  거래 상태. 비어 있으면 전체. 거래완료를 숨기려면 SELLING·RESERVED를 넘긴다.
 */
public record UsedProductSearchCondition(
        Long regionId,
        String keyword,
        List<Long> categoryIds,
        UsedProductPriceType priceType,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<UsedProductStatus> statuses
) {
    public static UsedProductSearchCondition ofRegion(Long regionId) {
        return new UsedProductSearchCondition(regionId, null, null, null, null, null, null);
    }
}
