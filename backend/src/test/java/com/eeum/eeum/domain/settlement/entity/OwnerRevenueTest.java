package com.eeum.eeum.domain.settlement.entity;

import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OwnerRevenueTest {

    @Test
    void 결제_완료_원장은_주문_완료_전에는_정산_대상_시각_없이_생성된다() {
        // given
        OwnerRevenue revenue = createRevenue();
        // when
        revenue.markSettleableAtFromCompletedOrder();
        revenue.markSettlementPending();
        revenue.markSettled();

        // then
        assertThat(revenue.getSettleableAt()).isEqualTo(LocalDateTime.of(2026, 9, 17, 12, 0));
        assertThat(revenue.getStatus()).isEqualTo(OwnerRevenueStatus.SETTLED);
    }

    @Test
    void 지급액이_수수료_계산식과_다르면_원장을_생성할_수_없다() {
        // given / when / then
        Order order = completedOrder();
        Payment payment = paidPaymentFor(order);
        assertThatThrownBy(() -> OwnerRevenue.create(
                order, payment, amount("10000"), amount("0"), amount("0"), amount("9999")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_INVALID_AMOUNT);
    }

    @Test
    void 지급이_완료된_원장은_취소할_수_없다() {
        // given
        OwnerRevenue revenue = createRevenue();
        revenue.markSettleableAtFromCompletedOrder();
        revenue.markSettlementPending();
        revenue.markSettled();

        // when / then
        assertThatThrownBy(() -> revenue.cancel("고객 취소", LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_INVALID_STATUS);
    }

    private OwnerRevenue createRevenue() {
        Order order = completedOrder();
        Payment payment = paidPaymentFor(order);
        return OwnerRevenue.create(
                order, payment, amount("10000"), amount("0"), amount("0"), amount("10000"));
    }

    private Order completedOrder() {
        Order order = mock(Order.class);
        when(order.getStore()).thenReturn(mock(Store.class));
        when(order.getOrderId()).thenReturn(1L);
        when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);
        when(order.getCompletedAt()).thenReturn(LocalDateTime.of(2026, 9, 10, 12, 0));
        return order;
    }

    private Payment paidPaymentFor(Order order) {
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn(PaymentStatus.PAID);
        when(payment.getOrder()).thenReturn(order);
        return payment;
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
