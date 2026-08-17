package com.eeum.eeum.domain.operation.repository;

import com.eeum.eeum.domain.operation.entity.OperationFailureLog;
import com.eeum.eeum.domain.operation.entity.QOperationFailureLog;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class OperationFailureLogRepositoryImpl implements OperationFailureLogRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<OperationFailureLog> searchFailures(
            OperationFailureCategory category,
            LocalDateTime from,
            LocalDateTime to,
            String keyword,
            Pageable pageable
    ) {
        QOperationFailureLog log = QOperationFailureLog.operationFailureLog;

        BooleanBuilder condition = new BooleanBuilder()
                .and(categoryEq(log, category))
                .and(createdAfter(log, from))
                .and(createdBefore(log, to))
                .and(keywordContains(log, keyword));

        List<OperationFailureLog> content = queryFactory
                .selectFrom(log)
                .where(condition)
                .orderBy(log.createdAt.desc(), log.operationFailureLogId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(log.count())
                .from(log)
                .where(condition)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
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

    private BooleanExpression categoryEq(QOperationFailureLog log, OperationFailureCategory category) {
        return category == null ? null : log.category.eq(category);
    }

    private BooleanExpression createdAfter(QOperationFailureLog log, LocalDateTime from) {
        return from == null ? null : log.createdAt.goe(from);
    }

    private BooleanExpression createdBefore(QOperationFailureLog log, LocalDateTime to) {
        return to == null ? null : log.createdAt.loe(to);
    }

    private BooleanExpression keywordContains(QOperationFailureLog log, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        return log.operation.contains(trimmed)
                .or(log.errorCode.contains(trimmed))
                .or(log.errorMessage.contains(trimmed));
    }
}
