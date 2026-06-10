package com.eeum.eeum.domain.favorite.repository;

// 찜 통계 쿼리 결과 프로젝션
public interface FavoriteStatProjection {

    Long getRefId();

    Long getFavoriteCount();
}