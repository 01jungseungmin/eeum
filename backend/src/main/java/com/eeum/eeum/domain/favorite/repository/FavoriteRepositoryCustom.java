package com.eeum.eeum.domain.favorite.repository;

import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface FavoriteRepositoryCustom {

    /**
     * 다수 대상 중 사용자가 찜한 refId Set 반환.
     * 목록 화면에서 N+1 없이 배치로 찜 여부를 표시할 때 사용한다.
     */
    Set<Long> findFavoriteRefIdsByAccountIdAndType(
            Long accountId,
            FavoriteRefType refType,
            List<Long> refIds
    );

    /**
     * 인기 항목 통계 — refType + 기간 필터 + 상위 N개.
     * 관리자 통계 화면에서 사용한다.
     */
    List<FavoriteStatProjection> findFavoriteStats(
            FavoriteRefType refType,
            LocalDateTime from,
            LocalDateTime to,
            int limit
    );
}