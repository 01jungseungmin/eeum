package com.eeum.eeum.domain.favorite.repository;

import com.eeum.eeum.common.dto.response.CursorSlice;
import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    /**
     * 중고 게시글 찜 목록 — 커서 무한 스크롤.
     *
     * <p>노출 조건(숨김·삭제 제외)을 페이징 전에 DB에서 걸러 페이지 크기를 보장한다.
     * Favorite은 FK 없는 polymorphic 참조지만 refId ↔ usedProductId theta join으로 조인할 수 있다.
     *
     * @param cursor 직전 페이지의 마지막 찜. 첫 페이지면 null이다.
     */
    CursorSlice<FavoriteUsedProductRow> findFavoriteUsedProducts(
            Long accountId, FavoriteCursor cursor, int size);

    // 상점 찜 목록 — 공개 조건(계정 활성·미정지·사장 승인)을 페이징과 count 전에 적용한다.
    // 조회 후 메모리에서 거르면 페이지 크기·전체 건수·페이지 경계가 모두 어긋난다.
    Page<FavoriteStoreRow> findFavoriteStores(Long accountId, Pageable pageable);

    // 무한 스크롤용 — count 쿼리 없이 다음 페이지 존재 여부만 판정한다.
    CursorSlice<FavoriteStoreRow> findFavoriteStoresSlice(
            Long accountId, FavoriteCursor cursor, int size);

    /**
     * 전체 찜 목록(상점 + 중고) — 커서 무한 스크롤.
     *
     * <p>대상 종류로 거르지 않는다. 공개 여부는 응답 조립 단계가 판단한다 —
     * 타입별 목록과 달리 이 목록은 "내가 찜한 것 전부"가 기준이다.
     */
    CursorSlice<Favorite> findMyFavorites(Long accountId, FavoriteCursor cursor, int size);
}