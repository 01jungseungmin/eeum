package com.eeum.eeum.domain.favorite.repository;

import com.eeum.eeum.domain.account.entity.QAccount;
import com.eeum.eeum.domain.account.entity.QRegion;
import com.eeum.eeum.common.dto.response.CursorSlice;
import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.entity.QFavorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.store.entity.QStore;
import com.eeum.eeum.domain.store.repository.StoreVisibilityPredicate;
import com.eeum.eeum.domain.used.entity.QUsedProduct;
import com.eeum.eeum.domain.used.repository.UsedProductVisibilityPredicate;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class FavoriteRepositoryImpl implements FavoriteRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QFavorite favorite = QFavorite.favorite;
    private final QUsedProduct usedProduct = QUsedProduct.usedProduct;
    private final QRegion region = QRegion.region;
    private final QStore store = QStore.store;
    private final QAccount account = QAccount.account;
    private final QAccount seller = new QAccount("seller");

    /**
     * 찜 목록 정렬 — 등록 최신순, 동률은 PK로 끊는다.
     *
     * <p>세 목록(전체·상점·중고)이 같은 정렬을 쓰므로 커서 규칙도 하나다.
     */
    private static final Sort FAVORITE_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("favoriteId"));

    // 목록 화면 배치 조회 — 사용자가 찜한 refId Set 반환 IN절 한 번으로 N+1을 방지

    @Override
    public Set<Long> findFavoriteRefIdsByAccountIdAndType(
            Long accountId,
            FavoriteRefType refType,
            List<Long> refIds
    ) {
        return queryFactory
                .select(favorite.refId)
                .from(favorite)
                .where(
                        favorite.account.accountId.eq(accountId),
                        favorite.refType.eq(refType),
                        favorite.refId.in(refIds)
                )
                .fetch()
                .stream()
                .collect(Collectors.toSet());
    }

    // 관리자 통계 — refType + 기간 필터 + 상위 N개 인기 항목
    @Override
    public List<FavoriteStatProjection> findFavoriteStats(
            FavoriteRefType refType,
            LocalDateTime from,
            LocalDateTime to,
            int limit
    ) {
        List<Tuple> tuples = queryFactory
                .select(favorite.refId, favorite.refId.count())
                .from(favorite)
                .where(
                        favorite.refType.eq(refType),
                        from != null ? favorite.createdAt.goe(from) : null,
                        to   != null ? favorite.createdAt.loe(to)   : null
                )
                .groupBy(favorite.refId)
                // 동률이면 limit 경계에서 어떤 항목이 잘릴지 매번 달라진다. refId로 순서를 고정한다.
                .orderBy(favorite.refId.count().desc(), favorite.refId.asc())
                .limit(limit)
                .fetch();

        return tuples.stream()
                .map(tuple -> new FavoriteStatProjection() {
                    @Override public Long getRefId()          { return tuple.get(favorite.refId); }
                    @Override public Long getFavoriteCount()  { return tuple.get(favorite.refId.count()); }
                })
                .collect(Collectors.toList());
    }

    // 실제 적용한 정렬(찜 등록 최신순 + PK tie-break)을 담은 Pageable
    private Pageable withAppliedSort(Pageable pageable) {
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("favoriteId")));
    }

    // 중고 게시글 찜 목록 — 숨김·삭제 필터를 페이징 전에 적용한다.
    // 조회 후 메모리에서 거르면 요청한 size보다 적은 항목이 내려가고 hasNext 판정도 어긋난다.
    // region까지 조인해 목록 조립 중 LAZY 초기화(항목 수만큼 추가 SELECT)가 없다.
    @Override
    public CursorSlice<FavoriteUsedProductRow> findFavoriteUsedProducts(
            Long accountId, FavoriteCursor cursor, int size) {
        List<FavoriteUsedProductRow> rows = new ArrayList<>(queryFactory
                .select(Projections.constructor(
                        FavoriteUsedProductRow.class,
                        favorite.favoriteId,
                        favorite.createdAt,
                        usedProduct.usedProductId,
                        usedProduct.title,
                        usedProduct.priceType,
                        usedProduct.price,
                        usedProduct.status,
                        region.dong))
                .from(favorite)
                .join(usedProduct).on(usedProduct.usedProductId.eq(favorite.refId))
                .join(usedProduct.region, region)
                .join(usedProduct.seller, seller)
                .where(
                        favorite.account.accountId.eq(accountId),
                        favorite.refType.eq(FavoriteRefType.USED_PRODUCT),
                        UsedProductVisibilityPredicate.publiclyVisible(usedProduct, seller),
                        afterCursor(cursor)
                )
                // createdAt 동률 시 순서가 흔들려 페이지 경계에서 항목이 중복·유실되므로 PK로 tie-break
                .orderBy(favorite.createdAt.desc(), favorite.favoriteId.desc())
                .limit(size + 1L)   // +1건으로 다음 페이지 존재 여부 판정 (count 쿼리 불필요)
                .fetch());

        return toCursorSlice(rows, size, FavoriteUsedProductRow::favoriteId,
                FavoriteUsedProductRow::favoritedAt);
    }

    // 상점 찜 목록(무한 스크롤) — 중고 게시글 목록과 같은 limit + 1 방식이라 count 쿼리가 없다.
    // 전체 건수를 쓰지 않는 화면에서 count는 순수 비용이다.
    @Override
    public CursorSlice<FavoriteStoreRow> findFavoriteStoresSlice(
            Long accountId, FavoriteCursor cursor, int size) {
        List<FavoriteStoreRow> rows = new ArrayList<>(favoriteStoreQuery(accountId)
                .where(afterCursor(cursor))
                .limit(size + 1L)   // +1건으로 다음 페이지 존재 여부 판정
                .fetch());

        return toCursorSlice(rows, size, FavoriteStoreRow::favoriteId, FavoriteStoreRow::favoritedAt);
    }

    /**
     * 전체 찜 목록 — 상점·중고를 가리지 않고 내가 찜한 순서대로 읽는다.
     *
     * <p>타입별 목록과 달리 대상의 공개 여부로 거르지 않는다. 이 목록의 기준은
     * "내가 찜한 것 전부"이고, 사라진 대상의 표시는 응답 조립 단계가 판단한다.
     */
    @Override
    public CursorSlice<Favorite> findMyFavorites(Long accountId, FavoriteCursor cursor, int size) {
        List<Favorite> rows = new ArrayList<>(queryFactory
                .selectFrom(favorite)
                .where(favorite.account.accountId.eq(accountId), afterCursor(cursor))
                .orderBy(favorite.createdAt.desc(), favorite.favoriteId.desc())
                .limit(size + 1L)
                .fetch());

        return toCursorSlice(rows, size, Favorite::getFavoriteId, Favorite::getCreatedAt);
    }

    /**
     * 커서 이후 구간. 정렬이 {@code createdAt desc, favoriteId desc}이므로
     * "더 이르게 찜했거나, 같은 시각이면 ID가 더 작은" 행들이다.
     *
     * <p>두 번째 항이 빠지면 같은 순간에 등록된 찜들 사이에서 경계를 끊지 못해
     * OFFSET과 같은 중복·누락이 그대로 재현된다.
     */
    private com.querydsl.core.types.dsl.BooleanExpression afterCursor(FavoriteCursor cursor) {
        if (cursor == null) {
            return null;
        }
        return favorite.createdAt.lt(cursor.createdAt())
                .or(favorite.createdAt.eq(cursor.createdAt())
                        .and(favorite.favoriteId.lt(cursor.favoriteId())));
    }

    /**
     * 한 건 더 읽어 다음 페이지 여부를 판정하고, 마지막 행에서 다음 커서를 만든다.
     *
     * <p>커서를 서버가 만들어 응답에 싣는 이유는, 목록마다 행 타입이 달라도 클라이언트가
     * 조립 규칙을 알 필요가 없게 하기 위해서다 — 받은 값을 그대로 되돌려보내면 된다.
     */
    private <T> CursorSlice<T> toCursorSlice(
            List<T> fetched, int size,
            java.util.function.Function<T, Long> idExtractor,
            java.util.function.Function<T, LocalDateTime> createdAtExtractor) {
        boolean hasNext = fetched.size() > size;
        List<T> content = hasNext ? new ArrayList<>(fetched.subList(0, size)) : fetched;
        T last = content.isEmpty() ? null : content.get(content.size() - 1);

        return CursorSlice.of(
                content,
                hasNext,
                last == null ? null : createdAtExtractor.apply(last).toString(),
                last == null ? null : idExtractor.apply(last),
                FAVORITE_SORT);
    }

    // 상점 찜 목록 — 공개 조건을 조인·where로 걸어 페이징과 count 이전에 적용한다.
    // Favorite은 FK 없는 polymorphic 참조지만 refId ↔ storeId theta join으로 묶을 수 있다.
    //
    // 번호 페이징 응답을 쓰는 레거시 경로(/favorites/me/STORE) 전용이다.
    // 신규 경로는 count 쿼리가 없는 findFavoriteStoresSlice를 쓴다.
    @Override
    public Page<FavoriteStoreRow> findFavoriteStores(Long accountId, Pageable pageable) {
        List<FavoriteStoreRow> rows = favoriteStoreQuery(accountId)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(favorite.count())
                .from(favorite)
                .join(store).on(store.storeId.eq(favorite.refId))
                .join(store.account, account)
                .where(
                        favorite.account.accountId.eq(accountId),
                        favorite.refType.eq(FavoriteRefType.STORE),
                        StoreVisibilityPredicate.publiclyVisible(store, account));

        // fetchOne()을 삼항 연산자 양쪽에서 호출하면 같은 count 쿼리가 두 번 실행된다.
        Long total = countQuery.fetchOne();
        return new PageImpl<>(rows, withAppliedSort(pageable), total == null ? 0L : total);
    }

    // 상점 찜 조회 본문 — Page/Slice 두 경로가 공개 조건과 정렬을 공유해야 한다.
    // 한쪽만 조건이 바뀌면 같은 화면인데 목록이 달라진다.
    private JPAQuery<FavoriteStoreRow> favoriteStoreQuery(Long accountId) {
        return queryFactory
                .select(Projections.constructor(
                        FavoriteStoreRow.class,
                        favorite.favoriteId,
                        favorite.createdAt,
                        store))
                .from(favorite)
                .join(store).on(store.storeId.eq(favorite.refId))
                .join(store.account, account)
                .where(
                        favorite.account.accountId.eq(accountId),
                        favorite.refType.eq(FavoriteRefType.STORE),
                        StoreVisibilityPredicate.publiclyVisible(store, account))
                // createdAt 동률 시 페이지 경계에서 항목이 중복·유실되므로 PK로 tie-break
                .orderBy(favorite.createdAt.desc(), favorite.favoriteId.desc());
    }
}