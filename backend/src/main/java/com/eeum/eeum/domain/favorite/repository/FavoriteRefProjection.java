package com.eeum.eeum.domain.favorite.repository;

import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;

/**
 * 찜의 대상 좌표만 읽는 projection.
 *
 * <p>favoriteId로 삭제할 때 "무엇을 잠글지" 정하려면 refType·refId가 필요한데,
 * 이때 Favorite을 엔티티로 읽으면 영속성 컨텍스트에 남아 잠금 후 재조회가 무의미해진다.
 */
public interface FavoriteRefProjection {

    FavoriteRefType getRefType();

    Long getRefId();
}
