package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.entity.QOrder;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QOrder order = QOrder.order;

    @Override
    public List<Long> findRegularAccountIds(Long storeId, OrderStatus status, long minOrderCount) {
        return queryFactory
                .select(order.account.accountId)
                .from(order)
                .where(
                        order.store.storeId.eq(storeId),
                        order.status.eq(status)
                )
                .groupBy(order.account.accountId)
                .having(order.count().goe(minOrderCount))
                .fetch();
    }

    @Override
    public List<Long> findInactiveRegularAccountIds(Long storeId, OrderStatus status, long minOrderCount,
            LocalDateTime inactiveSince) {
        return queryFactory
                .select(order.account.accountId)
                .from(order)
                .where(
                        order.store.storeId.eq(storeId),
                        order.status.eq(status)
                )
                .groupBy(order.account.accountId)
                .having(
                        order.count().goe(minOrderCount),
                        order.createdAt.max().lt(inactiveSince)
                )
                .fetch();
    }

    @Override
    public long countRegularAccounts(Long storeId, OrderStatus status, long minOrderCount) {
        // GROUP BY HAVING 결과를 서브쿼리로 카운트 — JPQL은 FROM 서브쿼리 미지원이므로 accountId 목록 크기로 대체.
        // 실제 단골 수는 수백~수천 수준으로 메모리 영향 미미하며, 쿼리 의도(count 전용)를 인터페이스에 명시.
        return queryFactory
                .select(order.account.accountId)
                .from(order)
                .where(
                        order.store.storeId.eq(storeId),
                        order.status.eq(status)
                )
                .groupBy(order.account.accountId)
                .having(order.count().goe(minOrderCount))
                .fetch()
                .size();
    }
}
