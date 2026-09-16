package com.eeum.eeum.domain.favorite.repository;

import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 중고 게시글 찜 목록 한 행 — Favorite ↔ UsedProduct ↔ Region을 한 쿼리로 조인해 담는다.
 * 엔티티가 아닌 필요한 컬럼만 담는 projection이라 목록 조립 중 LAZY 초기화가 발생하지 않는다.
 */
public record FavoriteUsedProductRow(
        Long favoriteId,
        LocalDateTime favoritedAt,
        Long usedProductId,
        String title,
        UsedProductPriceType priceType,
        BigDecimal price,
        UsedProductStatus status,
        String regionName
) {
}
