package com.eeum.eeum.domain.favorite.repository;

import com.eeum.eeum.domain.account.entity.QAccount;
import com.eeum.eeum.domain.account.entity.QRegion;
import com.eeum.eeum.domain.favorite.entity.QFavorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.store.entity.QStore;
import com.eeum.eeum.domain.store.repository.StoreVisibilityPredicate;
import com.eeum.eeum.domain.used.entity.QUsedProduct;
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
                .orderBy(favorite.refId.count().desc())
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
    public Slice<FavoriteUsedProductRow> findFavoriteUsedProducts(Long accountId, Pageable pageable) {
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
                .where(
                        favorite.account.accountId.eq(accountId),
                        favorite.refType.eq(FavoriteRefType.USED_PRODUCT),
                        usedProduct.hidden.isFalse(),
                        usedProduct.deletedAt.isNull()
                )
                // createdAt 동률 시 순서가 흔들려 페이지 경계에서 항목이 중복·유실되므로 PK로 tie-break
                .orderBy(favorite.createdAt.desc(), favorite.favoriteId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1L)   // +1건으로 다음 페이지 존재 여부 판정 (count 쿼리 불필요)
                .fetch());

        boolean hasNext = rows.size() > pageable.getPageSize();
        if (hasNext) {
            rows.remove(rows.size() - 1);
        }
        // 요청 sort는 무시하고 최신순으로 고정한다. 요청받은 Pageable을 그대로 돌려주면
        // 응답의 sort가 실제 적용된 정렬과 달라 클라이언트가 잘못된 순서를 전제하게 된다.
        return new SliceImpl<>(rows, withAppliedSort(pageable), hasNext);
    }

    // 상점 찜 목록 — 공개 조건을 조인·where로 걸어 페이징과 count 이전에 적용한다.
    // Favorite은 FK 없는 polymorphic 참조지만 refId ↔ storeId theta join으로 묶을 수 있다.
    @Override
    public Page<FavoriteStoreRow> findFavoriteStores(Long accountId, Pageable pageable) {
        List<FavoriteStoreRow> rows = queryFactory
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
                .orderBy(favorite.createdAt.desc(), favorite.favoriteId.desc())
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
}