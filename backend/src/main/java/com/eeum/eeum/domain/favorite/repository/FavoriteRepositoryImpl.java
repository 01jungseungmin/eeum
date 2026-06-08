package com.eeum.eeum.domain.favorite.repository;

import com.eeum.eeum.domain.favorite.entity.QFavorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class FavoriteRepositoryImpl implements FavoriteRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QFavorite favorite = QFavorite.favorite;

    /**
     * 목록 화면 배치 조회 — 사용자가 찜한 refId Set 반환.
     * IN절 한 번으로 N+1을 방지한다.
     */
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

    /**
     * 관리자 통계 — refType + 기간 필터 + 상위 N개 인기 항목.
     */
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
}