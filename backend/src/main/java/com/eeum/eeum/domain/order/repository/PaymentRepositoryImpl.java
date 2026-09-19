package com.eeum.eeum.domain.order.repository;

import com.eeum.eeum.domain.order.entity.QOrder;
import com.eeum.eeum.domain.order.entity.QPayment;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.store.entity.QStore;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepositoryCustom {

    private static final QPayment PAYMENT = QPayment.payment;
    private static final QOrder ORDER = QOrder.order;
    private static final QStore STORE = QStore.store;

    private final JPAQueryFactory queryFactory;

    // 상점명까지 한 번에 읽어 N+1을 피한다
    @Override
    public List<PaymentActivity> findRecentPaymentActivities(Collection<PaymentStatus> statuses, int limit) {
        return queryFactory
                .select(Projections.constructor(PaymentActivity.class,
                        PAYMENT.paymentId, STORE.name, PAYMENT.amount, PAYMENT.paidAt))
                .from(PAYMENT)
                .join(PAYMENT.order, ORDER)
                .join(ORDER.store, STORE)
                .where(
                        PAYMENT.paidAt.isNotNull(),
                        PAYMENT.status.in(statuses)
                )
                .orderBy(PAYMENT.paidAt.desc(), PAYMENT.paymentId.desc())
                .limit(limit)
                .fetch();
    }
}
