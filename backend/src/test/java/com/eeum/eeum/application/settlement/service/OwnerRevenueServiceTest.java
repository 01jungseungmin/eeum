package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.application.settlement.config.SettlementFeeProperties;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.settlement.entity.OwnerRevenue;
import com.eeum.eeum.domain.settlement.entity.WeeklySettlement;
import com.eeum.eeum.domain.settlement.entity.WeeklySettlementItem;
import com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus;
import com.eeum.eeum.domain.settlement.enums.WeeklySettlementStatus;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementItemRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerRevenueServiceTest {

    private static final Long ORDER_ID = 1L;
    private static final Long REVENUE_ID = 10L;
    private static final Long SETTLEMENT_ID = 100L;
    private static final String REASON = "고객 취소";

    @Mock private OwnerRevenueRepository ownerRevenueRepository;
    @Mock private WeeklySettlementRepository weeklySettlementRepository;
    @Mock private WeeklySettlementItemRepository weeklySettlementItemRepository;

    private OwnerRevenueService service;

    @BeforeEach
    void setUp() {
        service = createService("0", "0");
    }

    // ──────────────────── recordPaidOrder ────────────────────

    @Test
    void 현장결제는_수익_원장을_만들지_않는다() {
        // given — 손님이 매장에서 현금을 냈다. 플랫폼은 그 돈을 받은 적이 없다.
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn(PaymentStatus.PAID);
        when(payment.getPaymentMethod()).thenReturn(PaymentMethod.CASH_ON_SITE);

        // when
        OwnerRevenue revenue = service.recordPaidOrder(mock(Order.class), payment);

        // then — 원장을 만들면 주간 정산이 지급 대상으로 잡아 사장이 대금을 두 번 받는다
        assertThat(revenue).isNull();
        verify(ownerRevenueRepository, never()).findByOrder_OrderId(any());
        verify(ownerRevenueRepository, never()).save(any());
    }

    @Test
    void 설정된_수수료율이_원장_금액에_그대로_반영된다() {
        // given — 설정만 3%/2%로 바꾼 상황
        service = createService("0.03", "0.02");
        Order order = paidOrder();
        Payment payment = paidPayment(order, PaymentMethod.CARD);
        when(ownerRevenueRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(ownerRevenueRepository.save(any(OwnerRevenue.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        OwnerRevenue revenue = service.recordPaidOrder(order, payment);

        // then
        assertThat(revenue.getPgFeeAmount()).isEqualByComparingTo("300.00");
        assertThat(revenue.getPlatformFeeAmount()).isEqualByComparingTo("200.00");
        assertThat(revenue.getPayoutAmount()).isEqualByComparingTo("9500.00");
        assertThat(revenue.getStatus()).isEqualTo(OwnerRevenueStatus.ACCRUED);
    }

    @Test
    void 이미_원장이_있으면_다시_만들지_않는다() {
        // given — 결제 완료 경로가 여러 개라 같은 주문으로 두 번 들어올 수 있다
        Order order = mock(Order.class);
        when(order.getOrderId()).thenReturn(ORDER_ID);
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn(PaymentStatus.PAID);
        when(payment.getPaymentMethod()).thenReturn(PaymentMethod.CARD);
        OwnerRevenue existing = mock(OwnerRevenue.class);
        when(ownerRevenueRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.of(existing));

        // when
        OwnerRevenue revenue = service.recordPaidOrder(order, payment);

        // then
        assertThat(revenue).isSameAs(existing);
        verify(ownerRevenueRepository, never()).save(any());
    }

    // ──────────────────── markOrderCompleted ────────────────────

    @Test
    void 현장결제는_지급_가능_시각을_채우지_않는다() {
        // given
        Order order = mock(Order.class);
        when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);
        Payment payment = mock(Payment.class);
        when(payment.getPaymentMethod()).thenReturn(PaymentMethod.CASH_ON_SITE);

        // when
        service.markOrderCompleted(order, payment);

        // then — 원장이 없는 결제수단이라 조회 자체를 하지 않는다
        verify(ownerRevenueRepository, never()).findByOrder_OrderId(any());
    }

    @Test
    void 주문이_완료되면_원장에_지급_가능_시각이_채워진다() {
        // given
        Order order = mock(Order.class);
        when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);
        when(order.getOrderId()).thenReturn(ORDER_ID);
        Payment payment = mock(Payment.class);
        when(payment.getPaymentMethod()).thenReturn(PaymentMethod.CARD);
        OwnerRevenue revenue = mock(OwnerRevenue.class);
        when(ownerRevenueRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.of(revenue));

        // when
        service.markOrderCompleted(order, payment);

        // then
        verify(revenue).markSettleableAtFromCompletedOrder();
    }

    // ──────────────────── cancelBeforePayout ────────────────────

    @Test
    void 원장이_없는_주문은_취소할_대상도_없다() {
        // given — 결제 완료 전 주문
        when(ownerRevenueRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.empty());

        // when
        service.cancelBeforePayout(ORDER_ID, REASON, LocalDateTime.now());

        // then
        verify(ownerRevenueRepository, never()).findByOrderIdWithPessimisticLock(any());
    }

    @Test
    void 아직_정산에_포함되지_않은_원장은_그대로_취소한다() {
        // given
        OwnerRevenue revenue = lockedRevenueForCancel();
        when(weeklySettlementRepository.findByOwnerRevenueIdWithPessimisticLock(REVENUE_ID))
                .thenReturn(Optional.empty());
        when(weeklySettlementItemRepository.findByOwnerRevenue_OwnerRevenueId(REVENUE_ID))
                .thenReturn(Optional.empty());
        LocalDateTime cancelledAt = LocalDateTime.now();

        // when
        service.cancelBeforePayout(ORDER_ID, REASON, cancelledAt);

        // then
        verify(revenue).cancel(REASON, cancelledAt);
    }

    @Test
    void 스냅샷_이후_다른_경로가_먼저_취소했으면_재시도로_넘긴다() {
        // given — 정산 행은 잠갔는데 항목이 사라졌다
        lockedRevenueForCancel();
        // 항목이 사라진 것만으로 곧장 막으므로 정산 ID까지는 읽지 않는다
        when(weeklySettlementRepository.findByOwnerRevenueIdWithPessimisticLock(REVENUE_ID))
                .thenReturn(Optional.of(mock(WeeklySettlement.class)));
        when(weeklySettlementItemRepository.findByOwnerRevenue_OwnerRevenueId(REVENUE_ID))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.cancelBeforePayout(ORDER_ID, REASON, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_CONCURRENT_MODIFICATION);
    }

    @Test
    void 잠그지_않은_정산의_합계는_건드리지_않는다() {
        // given — 스냅샷에는 항목이 없어 정산을 잠그지 못했는데, 다시 읽으니 항목이 생겼다
        lockedRevenueForCancel();
        when(weeklySettlementRepository.findByOwnerRevenueIdWithPessimisticLock(REVENUE_ID))
                .thenReturn(Optional.empty());
        when(weeklySettlementItemRepository.findByOwnerRevenue_OwnerRevenueId(REVENUE_ID))
                .thenReturn(Optional.of(mock(WeeklySettlementItem.class)));

        // when / then — 잠금 순서를 뒤집는 대신 재시도로 넘긴다
        assertThatThrownBy(() -> service.cancelBeforePayout(ORDER_ID, REASON, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_CONCURRENT_MODIFICATION);
    }

    @Test
    void 정산에_포함된_원장은_합계_차감과_함께_취소한다() {
        // given
        OwnerRevenue revenue = lockedRevenueForCancel();
        WeeklySettlement locked = settlement(SETTLEMENT_ID);
        WeeklySettlementItem item = mock(WeeklySettlementItem.class);
        when(item.getWeeklySettlement()).thenReturn(locked);
        when(weeklySettlementRepository.findByOwnerRevenueIdWithPessimisticLock(REVENUE_ID))
                .thenReturn(Optional.of(locked));
        when(weeklySettlementItemRepository.findByOwnerRevenue_OwnerRevenueId(REVENUE_ID))
                .thenReturn(Optional.of(item));
        LocalDateTime cancelledAt = LocalDateTime.now();

        // when
        service.cancelBeforePayout(ORDER_ID, REASON, cancelledAt);

        // then
        verify(revenue).cancelBeforePayout(item, REASON, cancelledAt);
        verify(weeklySettlementItemRepository).delete(item);
    }

    // ──────────────────── assertCancellableBeforePayout ────────────────────

    @Test
    void 원장이_없으면_자동_취소를_막지_않는다() {
        // given
        when(ownerRevenueRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatCode(() -> service.assertCancellableBeforePayout(ORDER_ID)).doesNotThrowAnyException();
    }

    @Test
    void 이미_지급된_원장은_자동_취소할_수_없다() {
        // given
        lockedRevenueForAssert(OwnerRevenueStatus.SETTLED);
        when(weeklySettlementRepository.findByOwnerRevenueIdWithPessimisticLock(REVENUE_ID))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.assertCancellableBeforePayout(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_CANCELLATION_PAYOUT_STARTED);
    }

    @Test
    void 원장이_정산_대기여도_지급이_시작된_정산이면_자동_취소할_수_없다() {
        // given — 원장 상태만 보면 통과한다. 정산 행까지 봐야 과지급을 막는다.
        lockedRevenueForAssert(OwnerRevenueStatus.SETTLEMENT_PENDING);
        WeeklySettlement inProgress = mock(WeeklySettlement.class);
        when(inProgress.getStatus()).thenReturn(WeeklySettlementStatus.PAYOUT_IN_PROGRESS);
        when(weeklySettlementRepository.findByOwnerRevenueIdWithPessimisticLock(REVENUE_ID))
                .thenReturn(Optional.of(inProgress));

        // when / then
        assertThatThrownBy(() -> service.assertCancellableBeforePayout(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_CANCELLATION_PAYOUT_STARTED);
    }

    // ─────────────────── 헬퍼 ───────────────────

    private OwnerRevenueService createService(String pgRate, String platformRate) {
        return new OwnerRevenueService(
                ownerRevenueRepository,
                weeklySettlementRepository,
                weeklySettlementItemRepository,
                new SettlementFeePolicy(new SettlementFeeProperties(
                        new BigDecimal(pgRate), new BigDecimal(platformRate))));
    }

    /** 취소 반영 경로. 잠근 원장의 ID로 정산 항목을 다시 찾는다 — 상태는 보지 않는다. */
    private OwnerRevenue lockedRevenueForCancel() {
        OwnerRevenue locked = mock(OwnerRevenue.class);
        when(locked.getOwnerRevenueId()).thenReturn(REVENUE_ID);
        givenSnapshotAndLocked(locked);
        return locked;
    }

    /** 취소 가능 판단 경로. 잠근 원장의 상태만 본다 — ID는 스냅샷 것을 쓴다. */
    private OwnerRevenue lockedRevenueForAssert(OwnerRevenueStatus status) {
        OwnerRevenue locked = mock(OwnerRevenue.class);
        when(locked.getStatus()).thenReturn(status);
        givenSnapshotAndLocked(locked);
        return locked;
    }

    private void givenSnapshotAndLocked(OwnerRevenue locked) {
        OwnerRevenue snapshot = mock(OwnerRevenue.class);
        when(snapshot.getOwnerRevenueId()).thenReturn(REVENUE_ID);
        when(ownerRevenueRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.of(snapshot));
        when(ownerRevenueRepository.findByOrderIdWithPessimisticLock(ORDER_ID)).thenReturn(Optional.of(locked));
    }

    private WeeklySettlement settlement(Long settlementId) {
        WeeklySettlement settlement = mock(WeeklySettlement.class);
        when(settlement.getWeeklySettlementId()).thenReturn(settlementId);
        return settlement;
    }

    private Order paidOrder() {
        Order order = mock(Order.class);
        when(order.getOrderId()).thenReturn(ORDER_ID);
        when(order.getStore()).thenReturn(mock(Store.class));
        return order;
    }

    private Payment paidPayment(Order order, PaymentMethod method) {
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn(PaymentStatus.PAID);
        when(payment.getPaymentMethod()).thenReturn(method);
        when(payment.getOrder()).thenReturn(order);
        when(payment.getAmount()).thenReturn(new BigDecimal("10000"));
        return payment;
    }
}
