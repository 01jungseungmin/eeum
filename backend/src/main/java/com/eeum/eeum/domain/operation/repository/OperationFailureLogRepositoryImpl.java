package com.eeum.eeum.domain.operation.repository;

import com.eeum.eeum.domain.operation.entity.OperationFailureLog;
import com.eeum.eeum.domain.operation.entity.QOperationFailureLog;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class OperationFailureLogRepositoryImpl implements OperationFailureLogRepositoryCustom {

    private static final QOperationFailureLog LOG = QOperationFailureLog.operationFailureLog;

    /** 정렬 허용 필드 — 화이트리스트 밖의 property는 무시하고 기본 정렬로 되돌린다. */
    private static final Map<String, ComparableExpressionBase<?>> SORTABLE = Map.of(
            "createdAt", LOG.createdAt,
            "category", LOG.category,
            "operation", LOG.operation,
            "errorCode", LOG.errorCode
    );

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<OperationFailureLog> searchFailures(
            OperationFailureCategory category,
            LocalDateTime from,
            LocalDateTime to,
            String keyword,
            Pageable pageable
    ) {
        BooleanBuilder condition = new BooleanBuilder()
                .and(categoryEq(category))
                .and(createdAfter(LOG, from))
                .and(createdBefore(to))
                .and(keywordContains(keyword));

        Sort appliedSort = resolveSort(pageable.getSort());

        List<OperationFailureLog> content = queryFactory
                .selectFrom(LOG)
                .where(condition)
                .orderBy(toOrderSpecifiers(appliedSort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(LOG.count())
                .from(LOG)
                .where(condition)
                .fetchOne();

        // 실제로 적용한 정렬을 담아 돌려준다 — 무시한 정렬이 메타데이터에 남으면
        // 클라이언트가 정렬됐다고 오해한다.
        Pageable appliedPageable =
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), appliedSort);

        return new PageImpl<>(content, appliedPageable, total == null ? 0L : total);
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
                orders.add(order.isAscending() ? path.asc() : path.desc());
            }
        }
        // 동점 시 페이지 경계가 흔들리지 않도록 고유 키를 마지막에 붙인다.
        orders.add(LOG.operationFailureLogId.desc());
        return orders.toArray(new OrderSpecifier<?>[0]);
    }

    @Override
    public Map<OperationFailureCategory, Long> countByCategorySince(LocalDateTime since) {
        QOperationFailureLog log = QOperationFailureLog.operationFailureLog;

        List<Tuple> rows = queryFactory
                .select(log.category, log.count())
                .from(log)
                .where(createdAfter(log, since))
                .groupBy(log.category)
                .fetch();

        // 발생하지 않은 카테고리도 0으로 채워 응답 형태를 고정한다 —
        // 키가 빠지면 프론트가 매번 존재 여부를 확인해야 한다.
        Map<OperationFailureCategory, Long> result = new EnumMap<>(OperationFailureCategory.class);
        for (OperationFailureCategory category : OperationFailureCategory.values()) {
            result.put(category, 0L);
        }
        for (Tuple row : rows) {
            OperationFailureCategory category = row.get(log.category);
            Long count = row.get(log.count());
            if (category != null) {
                result.put(category, count == null ? 0L : count);
            }
        }
        return result;
    }

    @Override
    public List<OperationFailureLog> findRecentFailures(int limit) {
        QOperationFailureLog log = QOperationFailureLog.operationFailureLog;
        return queryFactory
                .selectFrom(log)
                .orderBy(log.createdAt.desc(), log.operationFailureLogId.desc())
                .limit(limit)
                .fetch();
    }

    private BooleanExpression categoryEq(OperationFailureCategory category) {
        return category == null ? null : LOG.category.eq(category);
    }

    private BooleanExpression createdAfter(QOperationFailureLog log, LocalDateTime from) {
        return from == null ? null : log.createdAt.goe(from);
    }

    private BooleanExpression createdBefore(LocalDateTime to) {
        return to == null ? null : LOG.createdAt.loe(to);
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        return LOG.operation.contains(trimmed)
                .or(LOG.errorCode.contains(trimmed))
                .or(LOG.errorMessage.contains(trimmed));
    }
}
