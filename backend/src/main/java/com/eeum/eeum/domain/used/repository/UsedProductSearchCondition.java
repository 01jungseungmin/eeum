package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * 중고 게시글 검색 조건.
 *
 * <p>도메인 패키지에 둔다 — 리포지토리가 application 레이어의 DTO를 참조하면
 * 의존 방향이 뒤집힌다. 컨트롤러가 받은 요청 파라미터로 서비스가 이 객체를 만든다.
 *
 * @param regionId  조회할 동네. 유일한 필수 조건이다.
 * @param keyword   제목·본문 검색어
 * @param categoryId 카테고리. 하위 카테고리는 포함하지 않는다(정확히 일치하는 것만).
 * @param priceType 거래 유형(정가/나눔/가격제안)
 * @param minPrice  최소 가격
 * @param maxPrice  최대 가격
 * @param statuses  거래 상태. 비어 있으면 전체. 거래완료를 숨기려면 SELLING·RESERVED를 넘긴다.
 */
public record UsedProductSearchCondition(
        Long regionId,
        String keyword,
        Long categoryId,
        UsedProductPriceType priceType,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<UsedProductStatus> statuses
) {
    public static UsedProductSearchCondition ofRegion(Long regionId) {
        return new UsedProductSearchCondition(regionId, null, null, null, null, null, null);
    }
}
