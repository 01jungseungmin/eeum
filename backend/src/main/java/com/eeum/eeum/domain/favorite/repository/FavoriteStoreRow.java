package com.eeum.eeum.domain.favorite.repository;

import com.eeum.eeum.domain.store.entity.Store;

import java.time.LocalDateTime;

/**
 * 상점 찜 목록 한 행 — Favorite과 공개 조건을 통과한 Store를 한 쿼리로 묶는다.
 * 응답 DTO가 Store의 여러 필드를 쓰므로 엔티티를 그대로 담는다.
 */
public record FavoriteStoreRow(
        Long favoriteId,
        LocalDateTime favoritedAt,
        Store store
) {
}
