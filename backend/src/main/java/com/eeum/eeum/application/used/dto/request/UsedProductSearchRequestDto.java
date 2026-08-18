package com.eeum.eeum.application.used.dto.request;

import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 중고 게시글 검색 요청.
 *
 * <p>컨트롤러가 받은 쿼리 파라미터를 담는다. 서비스가 지역을 확정한 뒤
 * 도메인 검색 조건으로 옮긴다 — 컨트롤러가 리포지토리 패키지를 직접 참조하지 않도록 한 단계 둔다.
 */
@Getter
@AllArgsConstructor
public class UsedProductSearchRequestDto {

    private Long regionId;
    private String keyword;
    private Long categoryId;
    private UsedProductPriceType priceType;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private List<UsedProductStatus> statuses;
}
