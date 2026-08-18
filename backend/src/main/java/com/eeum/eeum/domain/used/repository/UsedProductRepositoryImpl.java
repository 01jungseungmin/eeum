package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.QUsedProduct;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class UsedProductRepositoryImpl implements UsedProductRepositoryCustom {

    private static final QUsedProduct PRODUCT = QUsedProduct.usedProduct;

    // 정렬 허용 필드
    private static final Map<String, ComparableExpressionBase<?>> SORTABLE = Map.of(
            "createdAt", PRODUCT.createdAt,
            "price", PRODUCT.price,
            "favoriteCount", PRODUCT.favoriteCount,
            "viewCount", PRODUCT.viewCount
    );

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final JPAQueryFactory queryFactory;

    @Override
    public Slice<UsedProduct> findByRegions(Collection<Long> regionIds, Pageable pageable) {
        int size = pageable.getPageSize();
        Sort appliedSort = resolveSort(pageable.getSort());

        List<UsedProduct> content = queryFactory
                .selectFrom(PRODUCT)
                // 목록 DTO가 지역명과 카테고리명을 바로 읽기 fetch join이 없으면 페이지 크기만큼 추가 select가 나감(N+1)
                .leftJoin(PRODUCT.region).fetchJoin()
                .leftJoin(PRODUCT.category).fetchJoin()
                .where(
                        PRODUCT.region.regionId.in(regionIds),
                        // 삭제·숨김은 모든 사용자 조회에서 빠짐없이 거르기
                        PRODUCT.deletedAt.isNull(),
                        PRODUCT.hidden.isFalse()
                )
                .orderBy(toOrderSpecifiers(appliedSort))
                .offset(pageable.getOffset())
                // 다음 페이지 존재 여부만 알면 되므로 한 건 더 읽고 자르기
                .limit(size + 1L)
                .fetch();

        boolean hasNext = content.size() > size;
        if (hasNext) {
            content = content.subList(0, size);
        }

        // 실제로 적용한 정렬을 담아 돌리기 — 무시한 정렬이 메타데이터에 남으면 클라이언트가 정렬됐다고 오해
        Pageable appliedPageable = PageRequest.of(pageable.getPageNumber(), size, appliedSort);
        return new SliceImpl<>(content, appliedPageable, hasNext);
    }

    @Override
    public void increaseViewCount(Long usedProductId) {
        queryFactory
                .update(PRODUCT)
                .set(PRODUCT.viewCount, PRODUCT.viewCount.add(1))
                .where(PRODUCT.usedProductId.eq(usedProductId))
                .execute();
    }

    private Sort resolveSort(Sort requested) {
        List<Sort.Order> applied = requested.stream()
                .filter(order -> SORTABLE.containsKey(order.getProperty()))
                .toList();
        return applied.isEmpty() ? DEFAULT_SORT : Sort.by(applied);
    }

    private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            ComparableExpressionBase<?> path = SORTABLE.get(order.getProperty());
            if (path != null) {
                // NEGOTIABLE은 price가 null 가격순 정렬에서 맨 뒤로 보내기
                orders.add(order.isAscending() ? path.asc().nullsLast() : path.desc().nullsLast());
            }
        }
        // 동점 시 페이지 경계가 흔들리지 않도록 고유 키를 마지막에 붙이기
        orders.add(PRODUCT.usedProductId.desc());
        return orders.toArray(new OrderSpecifier<?>[0]);
    }
}
