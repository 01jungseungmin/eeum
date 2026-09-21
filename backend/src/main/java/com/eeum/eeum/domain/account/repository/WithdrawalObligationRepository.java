package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.order.enums.*;
import com.eeum.eeum.domain.reservation.enums.VisitReservationStatus;
import com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus;
import com.eeum.eeum.domain.settlement.enums.WeeklySettlementStatus;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import static com.eeum.eeum.domain.order.entity.QOrder.order;
import static com.eeum.eeum.domain.order.entity.QPayment.payment;
import static com.eeum.eeum.domain.order.entity.QPaymentCancellationOperation.paymentCancellationOperation;
import static com.eeum.eeum.domain.reservation.entity.QVisitReservation.visitReservation;
import static com.eeum.eeum.domain.settlement.entity.QOwnerRevenue.ownerRevenue;
import static com.eeum.eeum.domain.settlement.entity.QWeeklySettlement.weeklySettlement;
import static com.eeum.eeum.domain.store.entity.QStore.store;
import static com.eeum.eeum.domain.used.entity.QUsedProduct.usedProduct;

@Repository
@RequiredArgsConstructor
public class WithdrawalObligationRepository {
    private final JPAQueryFactory query;

    // payment/취소작업 -> order -> store -> account는 Q클래스 경로 초기화 깊이(2)를 넘어
    // 점 표기로 쓰면 NPE가 난다. 이 구간만 명시적 조인으로 탄다.

    public boolean hasPendingOrders(Long accountId) {
        return query.selectOne().from(order)
                .where(order.account.accountId.eq(accountId).or(order.store.account.accountId.eq(accountId)),
                        order.status.in(OrderStatus.PENDING, OrderStatus.PAID, OrderStatus.CONFIRMED, OrderStatus.READY))
                .fetchFirst() != null
                || query.selectOne().from(payment)
                .join(payment.order, order)
                .join(order.store, store)
                .where(payment.account.accountId.eq(accountId).or(store.account.accountId.eq(accountId)),
                        payment.refundStatus.eq(RefundStatus.REQUESTED))
                .fetchFirst() != null
                || query.selectOne().from(paymentCancellationOperation)
                .join(paymentCancellationOperation.order, order)
                .join(order.store, store)
                .where(order.account.accountId.eq(accountId)
                                .or(store.account.accountId.eq(accountId)),
                        paymentCancellationOperation.status.ne(PaymentCancellationStatus.COMPLETED))
                .fetchFirst() != null;
    }

    public boolean hasPendingReservations(Long accountId) {
        return query.selectOne().from(visitReservation)
                .where(visitReservation.account.accountId.eq(accountId)
                                .or(visitReservation.store.account.accountId.eq(accountId)),
                        visitReservation.status.in(VisitReservationStatus.PENDING, VisitReservationStatus.APPROVED))
                .fetchFirst() != null
                || query.selectOne().from(usedProduct)
                .where(usedProduct.seller.accountId.eq(accountId).or(usedProduct.buyer.accountId.eq(accountId)),
                        usedProduct.status.eq(UsedProductStatus.RESERVED))
                .fetchFirst() != null;
    }

    public boolean hasPendingSettlements(Long accountId) {
        return query.selectOne().from(ownerRevenue)
                .where(ownerRevenue.store.account.accountId.eq(accountId),
                        ownerRevenue.status.in(OwnerRevenueStatus.ACCRUED, OwnerRevenueStatus.SETTLEMENT_PENDING))
                .fetchFirst() != null
                || query.selectOne().from(weeklySettlement)
                .where(weeklySettlement.store.account.accountId.eq(accountId),
                        weeklySettlement.status.ne(WeeklySettlementStatus.COMPLETED))
                .fetchFirst() != null
                // 원장 생성 실패/지연도 지급 완료로 오인하지 않는다.
                || query.selectOne().from(payment)
                .join(payment.order, order)
                .join(order.store, store)
                .where(store.account.accountId.eq(accountId),
                        payment.paymentMethod.ne(PaymentMethod.CASH_ON_SITE),
                        payment.status.in(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED),
                        JPAExpressions.selectOne().from(ownerRevenue)
                                .where(ownerRevenue.order.orderId.eq(order.orderId)).notExists())
                .fetchFirst() != null;
    }

    public boolean requiresSettlementAccount(Long accountId) {
        return hasPendingSettlements(accountId)
                || query.selectOne().from(payment)
                .join(payment.order, order)
                .join(order.store, store)
                .where(store.account.accountId.eq(accountId),
                        payment.paymentMethod.ne(PaymentMethod.CASH_ON_SITE),
                        payment.status.in(PaymentStatus.PENDING, PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED))
                .fetchFirst() != null
                || query.selectOne().from(paymentCancellationOperation)
                .join(paymentCancellationOperation.order, order)
                .join(order.store, store)
                .where(store.account.accountId.eq(accountId),
                        paymentCancellationOperation.status.ne(PaymentCancellationStatus.COMPLETED))
                .fetchFirst() != null;
    }
}
