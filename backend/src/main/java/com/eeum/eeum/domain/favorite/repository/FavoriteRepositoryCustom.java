package com.eeum.eeum.domain.favorite.repository;

import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface FavoriteRepositoryCustom {

    // 다수 대상 중 사용자가 찜한 refId Set 반환 - 목록 화면에서 N+1 없이 배치로 찜 여부를 표시할 때 사용
    Set<Long> findFavoriteRefIdsByAccountIdAndType(
            Long accountId,
            FavoriteRefType refType,
            List<Long> refIds
    );

    // 인기 항목 통계 — refType + 기간 필터 + 상위 N개 - 관리자 통계 화면에서 사용

    List<FavoriteStatProjection> findFavoriteStats(
            FavoriteRefType refType,
            LocalDateTime from,
            LocalDateTime to,
            int limit
    );

    // 중고 게시글 찜 목록 — 노출 조건(숨김·삭제 제외)을 페이징 전에 DB에서 걸러 페이지 크기를 보장한다.
    // Favorite은 FK 없는 polymorphic 참조지만 refId ↔ usedProductId theta join으로 조인할 수 있다.
    Slice<FavoriteUsedProductRow> findFavoriteUsedProducts(Long accountId, Pageable pageable);
}