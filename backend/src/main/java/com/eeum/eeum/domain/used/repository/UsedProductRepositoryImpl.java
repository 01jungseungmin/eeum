package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.account.entity.QAccount;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.used.entity.QUsedProduct;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public class UsedProductRepositoryImpl implements UsedProductRepositoryCustom {

    private static final QUsedProduct PRODUCT = QUsedProduct.usedProduct;
    private static final QAccount SELLER = QAccount.account;

    // 정렬 허용 필드
    private static final Map<String, ComparableExpressionBase<?>> SORTABLE = Map.of(
            "createdAt", PRODUCT.createdAt,
            "price", PRODUCT.price,
            "favoriteCount", PRODUCT.favoriteCount,
            "viewCount", PRODUCT.viewCount
    );

    // NULL이 섞일 수 있는 정렬 필드. NEGOTIABLE(가격제안)은 price가 null이라
    // 가격순 정렬에서 맨 뒤로 보낸다. 나머지 필드는 NOT NULL이라 NULL 처리를 지정하지 않는다 —
    // 지정해 봐야 SQL에 불필요한 case 식만 붙는다.
    private static final Set<String> NULLABLE_SORT_PROPERTIES = Set.of("price");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    // 동점 시 페이지 경계가 흔들리지 않도록 항상 마지막에 붙는 고유 키.
    // SORTABLE에는 넣지 않는다 — 클라이언트가 직접 지정할 수 있는 정렬 필드가 아니다.
    private static final String TIE_BREAK_PROPERTY = "usedProductId";

    // SQL 변환용 경로 표 — 허용 정렬 필드에 tie-break 키를 더한 것.
    private static final Map<String, ComparableExpressionBase<?>> ORDER_PATHS = buildOrderPaths();

    private static Map<String, ComparableExpressionBase<?>> buildOrderPaths() {
        Map<String, ComparableExpressionBase<?>> paths = new HashMap<>(SORTABLE);
        paths.put(TIE_BREAK_PROPERTY, PRODUCT.usedProductId);
        return Map.copyOf(paths);
    }

    private final JPAQueryFactory queryFactory;

    @Override
    public Slice<UsedProduct> search(UsedProductSearchCondition condition, Pageable pageable) {
        int size = pageable.getPageSize();
        // tie-break까지 포함해 확정한다. SQL에만 붙이고 메타데이터에서 빠뜨리면
        // 응답 Slice.pageable.sort가 실제 정렬 순서와 달라진다.
        Sort appliedSort = resolveSort(pageable.getSort());

        List<UsedProduct> content = queryFactory
                .selectFrom(PRODUCT)
                // 목록 DTO가 지역명과 카테고리명을 바로 읽기 fetch join이 없으면 페이지 크기만큼 추가 select가 나감(N+1)
                .leftJoin(PRODUCT.region).fetchJoin()
                .leftJoin(PRODUCT.category).fetchJoin()
                .join(PRODUCT.seller, SELLER)
                .where(toPredicate(condition))
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
    public long increaseViewCount(Long usedProductId) {
        return queryFactory
                .update(PRODUCT)
                .set(PRODUCT.viewCount, PRODUCT.viewCount.add(1))
                .where(
                        PRODUCT.usedProductId.eq(usedProductId),
                        // 공개 확인과 이 UPDATE 사이에 숨김·삭제·판매자 탈퇴가 커밋될 수 있다.
                        // 조건을 UPDATE에 함께 걸어야 비공개 글의 조회수가 오르지 않는다.
                        // bulk UPDATE는 조인을 쓸 수 없어 판매자 조건은 FK IN 서브쿼리로 건다.
                        PRODUCT.deletedAt.isNull(),
                        PRODUCT.hidden.isFalse(),
                        PRODUCT.seller.accountId.in(
                                JPAExpressions.select(SELLER.accountId)
                                        .from(SELLER)
                                        .where(SELLER.status.eq(AccountStatus.ACTIVE))))
                .execute();
    }

    private BooleanBuilder toPredicate(UsedProductSearchCondition condition) {
        return new BooleanBuilder()
                .and(PRODUCT.region.regionId.eq(condition.regionId()))
                // 삭제·숨김·판매자 탈퇴는 모든 사용자 조회에서 빠짐없이 거른다
                .and(UsedProductVisibilityPredicate.publiclyVisible(PRODUCT, SELLER))
                .and(keywordContains(condition.keyword()))
                .and(categoryIn(condition.categoryIds()))
                .and(priceTypeEq(condition.priceType()))
                .and(priceGoe(condition.minPrice()))
                .and(priceLoe(condition.maxPrice()))
                .and(statusIn(condition.statuses()));
    }

    // 제목·본문 부분 일치. LIKE '%kw%'라 인덱스를 타지 못한다
    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        return PRODUCT.title.contains(trimmed).or(PRODUCT.content.contains(trimmed));
    }

    // 상위 카테고리를 고르면 하위 전체가 함께 온다. 서비스가 펼쳐서 넘긴다.
    private BooleanExpression categoryIn(List<Long> categoryIds) {
        return CollectionUtils.isEmpty(categoryIds) ? null : PRODUCT.category.categoryId.in(categoryIds);
    }

    private BooleanExpression priceTypeEq(UsedProductPriceType priceType) {
        return priceType == null ? null : PRODUCT.priceType.eq(priceType);
    }

    // 가격 범위를 지정하면 가격제안(price = null) 글은 자연히 빠진다 — NULL 비교는 참이 되지 않는다
    private BooleanExpression priceGoe(BigDecimal minPrice) {
        return minPrice == null ? null : PRODUCT.price.goe(minPrice);
    }

    private BooleanExpression priceLoe(BigDecimal maxPrice) {
        return maxPrice == null ? null : PRODUCT.price.loe(maxPrice);
    }

    // 비어 있으면 전체 상태를 본다. 거래완료를 숨기려면 SELLING·RESERVED를 넘긴다
    private BooleanExpression statusIn(List<UsedProductStatus> statuses) {
        return CollectionUtils.isEmpty(statuses) ? null : PRODUCT.status.in(statuses);
    }

    private Sort resolveSort(Sort requested) {
        List<Sort.Order> applied = requested.stream()
                .filter(order -> SORTABLE.containsKey(order.getProperty()))
                .map(UsedProductRepositoryImpl::normalizeNullHandling)
                .toList();
        Sort base = applied.isEmpty() ? DEFAULT_SORT : Sort.by(applied);
        // 동점 시 페이지 경계가 흔들리지 않도록 고유 키를 마지막에 붙인다.
        // toOrderSpecifiers가 SQL에 넣는 것과 반드시 같아야 한다.
        return base.and(Sort.by(Sort.Order.desc(TIE_BREAK_PROPERTY)));
    }

    /**
     * NULL이 섞이는 정렬 필드의 NULL 처리를 확정한다.
     *
     * <p>요청 파라미터({@code sort=price,asc})로는 NULL 처리를 지정할 수 없어 항상 NATIVE로 들어온다.
     * 그대로 두면 응답 메타데이터는 NATIVE(MySQL ASC 기준 NULL이 앞)라고 말하는데 실제 결과는
     * NULL이 뒤에 오는, <b>정반대</b> 상태가 된다. 여기서 확정하고 SQL은 이 값을 따라간다.
     */
    private static Sort.Order normalizeNullHandling(Sort.Order order) {
        return NULLABLE_SORT_PROPERTIES.contains(order.getProperty()) ? order.nullsLast() : order;
    }

    private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            ComparableExpressionBase<?> path = ORDER_PATHS.get(order.getProperty());
            if (path != null) {
                // NULL 처리는 resolveSort가 확정한 메타데이터를 그대로 따른다.
                // 여기서 독립적으로 붙이면 응답 Sort와 실제 SQL이 갈린다.
                orders.add(applyNullHandling(
                        order.isAscending() ? path.asc() : path.desc(),
                        order.getNullHandling()));
            }
        }
        return orders.toArray(new OrderSpecifier<?>[0]);
    }

    private static OrderSpecifier<?> applyNullHandling(
            OrderSpecifier<?> specifier,
            Sort.NullHandling nullHandling
    ) {
        return switch (nullHandling) {
            case NULLS_LAST -> specifier.nullsLast();
            case NULLS_FIRST -> specifier.nullsFirst();
            case NATIVE -> specifier;
        };
    }
}
