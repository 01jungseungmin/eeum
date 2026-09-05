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
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import com.eeum.eeum.common.dto.response.CursorSlice;
import org.springframework.data.domain.Sort;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Function;

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

    /**
     * 커서(keyset) 페이징. OFFSET을 쓰지 않는다.
     *
     * <p>기본 정렬이 최신순이라 새 글이 맨 앞에 꽂힌다. OFFSET은 페이지 사이 등록 한 건에
     * 목록 전체가 밀려 경계 항목이 다음 페이지에서 중복으로 나온다(삭제되면 반대로 건너뛴다).
     *
     * <p>다음 커서는 서버가 만들어 응답에 싣는다({@code nextCursorValue}). 정렬 키가 무엇이냐에 따라
     * 값의 형식이 달라지므로, 그 규칙을 아는 쪽이 만들어야 클라이언트가 정렬마다 다른 조립을
     * 하지 않는다. 페이지 번호가 없는 계약이라 Slice가 아니라 {@link CursorSlice}로 돌려준다 —
     * Slice로 돌리면 두 번째 페이지에도 number=0, first=true가 실려 응답이 위치를 잘못 설명한다.
     */
    @Override
    public CursorSlice<UsedProduct> search(
            UsedProductSearchCondition condition, UsedProductCursor cursor, int size, Sort requestedSort) {
        // tie-break까지 포함해 확정한다. SQL에만 붙이고 메타데이터에서 빠뜨리면
        // 응답 CursorSlice.sort가 실제 정렬 순서와 달라진다.
        Sort appliedSort = resolveSort(requestedSort);

        List<UsedProduct> content = queryFactory
                .selectFrom(PRODUCT)
                // 목록 DTO가 지역명과 카테고리명을 바로 읽기 fetch join이 없으면 페이지 크기만큼 추가 select가 나감(N+1)
                .leftJoin(PRODUCT.region).fetchJoin()
                .leftJoin(PRODUCT.category).fetchJoin()
                .join(PRODUCT.seller, SELLER)
                .where(toPredicate(condition).and(afterCursor(appliedSort, cursor)))
                .orderBy(toOrderSpecifiers(appliedSort))
                // 다음 페이지 존재 여부만 알면 되므로 한 건 더 읽고 자르기
                .limit(size + 1L)
                .fetch();

        boolean hasNext = content.size() > size;
        if (hasNext) {
            content = content.subList(0, size);
        }
        UsedProduct last = content.isEmpty() ? null : content.get(content.size() - 1);

        // 실제로 적용한 정렬을 담아 돌리기 — 무시한 정렬이 메타데이터에 남으면 클라이언트가 정렬됐다고 오해
        return CursorSlice.of(
                content,
                hasNext,
                last == null ? null : nextCursorValue(appliedSort, last),
                last == null ? null : last.getUsedProductId(),
                appliedSort);
    }

    /**
     * 다음 커서에 실을 정렬 키 값. 적용된 정렬이 무엇이냐에 따라 형식이 달라진다.
     *
     * <p>가격제안 글(price null)에서는 null을 돌려준다 — 그 구간은 정렬상 맨 뒤라
     * 게시글 ID만으로 이어 읽는다. {@code afterCursor}의 NULL 분기와 짝을 이룬다.
     */
    private String nextCursorValue(Sort appliedSort, UsedProduct last) {
        return switch (appliedSort.iterator().next().getProperty()) {
            case "price" -> last.getPrice() == null ? null : last.getPrice().toPlainString();
            case "favoriteCount" -> String.valueOf(last.getFavoriteCount());
            case "viewCount" -> String.valueOf(last.getViewCount());
            // 기본 정렬이 createdAt이므로 나머지는 여기로 온다.
            default -> last.getCreatedAt().toString();
        };
    }

    /**
     * 커서 이후 구간. 정렬 키 하나 + tie-break(usedProductId desc) 조합으로 만든다.
     *
     * <p>tie 항이 빠지면 정렬 키가 같은 글들 사이에서 경계를 끊지 못해 OFFSET과 같은
     * 중복·누락이 그대로 재현된다 — 같은 초에 올라온 글, 같은 가격의 글에서 실제로 난다.
     *
     * <p>가격순에서 가격제안(price null) 글은 정렬상 맨 뒤다. 그래서 값이 있는 커서에는
     * {@code price IS NULL} 구간을 함께 붙이고, 커서 자체가 그 구간이면 ID로만 이어 읽는다.
     * 이 항을 빠뜨리면 가격제안 글이 목록에서 통째로 사라진다.
     */
    private BooleanExpression afterCursor(Sort appliedSort, UsedProductCursor cursor) {
        if (cursor == null) {
            return null;
        }
        Sort.Order primary = appliedSort.iterator().next();
        boolean asc = primary.isAscending();
        // tie-break는 방향과 무관하게 항상 ID 내림차순이다(resolveSort가 그렇게 붙인다).
        BooleanExpression tie = PRODUCT.usedProductId.lt(cursor.usedProductId());
        String raw = cursor.sortValue();

        return switch (primary.getProperty()) {
            case "createdAt" -> {
                LocalDateTime value = parseCursorValue(raw, LocalDateTime::parse);
                yield (asc ? PRODUCT.createdAt.gt(value) : PRODUCT.createdAt.lt(value))
                        .or(PRODUCT.createdAt.eq(value).and(tie));
            }
            case "price" -> {
                if (raw == null) {
                    yield PRODUCT.price.isNull().and(tie);
                }
                BigDecimal value = parseCursorValue(raw, BigDecimal::new);
                yield (asc ? PRODUCT.price.gt(value) : PRODUCT.price.lt(value))
                        .or(PRODUCT.price.eq(value).and(tie))
                        .or(PRODUCT.price.isNull());
            }
            case "favoriteCount" -> {
                Integer value = parseCursorValue(raw, Integer::valueOf);
                yield (asc ? PRODUCT.favoriteCount.gt(value) : PRODUCT.favoriteCount.lt(value))
                        .or(PRODUCT.favoriteCount.eq(value).and(tie));
            }
            case "viewCount" -> {
                Integer value = parseCursorValue(raw, Integer::valueOf);
                yield (asc ? PRODUCT.viewCount.gt(value) : PRODUCT.viewCount.lt(value))
                        .or(PRODUCT.viewCount.eq(value).and(tie));
            }
            // resolveSort가 허용 필드만 남기므로 여기까지 오지 않는다. 정렬 필드가 늘어났는데
            // 이 switch를 빠뜨리면 커서가 조용히 무시되어 목록이 반복되므로 명시적으로 막는다.
            default -> throw new BadRequestException(ErrorCode.USED_PRODUCT_INVALID_CURSOR);
        };
    }

    /**
     * 커서 값은 클라이언트가 응답에서 그대로 되돌려보내는 값이다. 형식이 어긋났다는 것은
     * 정렬을 바꾸고 이전 커서를 그대로 보냈다는 뜻이므로, 조용히 첫 페이지로 되돌리지 않고 알린다.
     */
    private static <T> T parseCursorValue(String raw, Function<String, T> parser) {
        if (raw == null) {
            throw new BadRequestException(ErrorCode.USED_PRODUCT_INVALID_CURSOR);
        }
        try {
            return parser.apply(raw);
        } catch (RuntimeException e) {
            throw new BadRequestException(ErrorCode.USED_PRODUCT_INVALID_CURSOR);
        }
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

    /**
     * 적용할 정렬 확정 — 허용 필드 <b>하나</b>와 tie-break.
     *
     * <p>정렬 키를 하나로 좁히는 것은 커서 페이징의 전제다. 키가 둘 이상이면 커서가 그 값을
     * 모두 담아야 하고, 클라이언트는 정렬 조합마다 다른 커서를 만들어야 한다.
     * 실제로 이 목록은 한 번에 한 기준으로만 정렬하므로 첫 번째 허용 필드만 쓴다.
     * 무엇이 적용됐는지는 응답 {@code CursorSlice.sort}에 그대로 실려 나간다.
     */
    private Sort resolveSort(Sort requested) {
        List<Sort.Order> applied = requested.stream()
                .filter(order -> SORTABLE.containsKey(order.getProperty()))
                .map(UsedProductRepositoryImpl::normalizeNullHandling)
                .limit(1)
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
