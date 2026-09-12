package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.settlement.entity.OwnerRevenue;
import com.eeum.eeum.domain.settlement.entity.WeeklySettlement;
import com.eeum.eeum.domain.settlement.entity.WeeklySettlementItem;
import com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementItemRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklySettlementClosingServiceTest {

    private static final Long REVENUE_ID = 10L;
    private static final Long STORE_ID = 1L;
    private static final LocalDateTime PERIOD_END = LocalDateTime.of(2026, 9, 14, 0, 0);
    private static final LocalDateTime PERIOD_START = PERIOD_END.minusWeeks(1);

    @Mock private OwnerRevenueRepository ownerRevenueRepository;
    @Mock private WeeklySettlementRepository weeklySettlementRepository;
    @Mock private WeeklySettlementItemRepository weeklySettlementItemRepository;

    @InjectMocks private WeeklySettlementClosingService service;

    private Store store;

    @BeforeEach
    void setUp() {
        store = Store.createForOwnerSignup(mock(Account.class), "테스트 상점", "서울시", "010-0000-0000");
        ReflectionTestUtils.setField(store, "storeId", STORE_ID);
    }

    @Test
    void 유보기간이_지난_원장은_그_주차_정산에_포함된다() {
        // given
        OwnerRevenue revenue = revenue(PERIOD_START.plusDays(2));
        WeeklySettlement settlement = settlement();
        givenLocked(revenue, settlement);

        // when
        boolean lateInclusion = service.closeEligibleRevenue(REVENUE_ID, PERIOD_START, PERIOD_END);

        // then
        assertThat(lateInclusion).isFalse();
        assertThat(revenue.getStatus()).isEqualTo(OwnerRevenueStatus.SETTLEMENT_PENDING);
        assertThat(settlement.getPayoutAmount()).isEqualByComparingTo("10000");
        verify(weeklySettlementItemRepository).save(any(WeeklySettlementItem.class));
    }

    @Test
    void 정산_행은_있으면_재사용하고_없으면_만든다() {
        // given — 같은 상점·기간에 여러 원장이 들어와도 행은 하나여야 한다
        OwnerRevenue revenue = revenue(PERIOD_START.plusDays(2));
        givenLocked(revenue, settlement());

        // when
        service.closeEligibleRevenue(REVENUE_ID, PERIOD_START, PERIOD_END);

        // then — 경쟁 없이 한 행만 남기는 upsert를 쓴다
        verify(weeklySettlementRepository).insertIfAbsent(
                STORE_ID, PERIOD_START, PERIOD_END, "weekly:" + STORE_ID + ":" + PERIOD_END);
    }

    @Test
    void 지난_마감에서_누락된_원장은_지연_포함으로_보고된다() {
        // given — 이번 주기 시작 전에 이미 지급 가능했던 원장
        OwnerRevenue revenue = revenue(PERIOD_START.minusDays(3));
        givenLocked(revenue, settlement());

        // when
        boolean lateInclusion = service.closeEligibleRevenue(REVENUE_ID, PERIOD_START, PERIOD_END);

        // then — 정산 행의 기간이 포함 원장을 설명하지 못하므로 호출자가 기록해야 한다
        assertThat(lateInclusion).isTrue();
        verify(weeklySettlementItemRepository).save(any(WeeklySettlementItem.class));
    }

    @Test
    void 아직_유보기간이_남은_원장은_마감하지_않는다() {
        // given
        OwnerRevenue revenue = revenue(PERIOD_END.plusDays(1));
        givenLocked(revenue, settlement());

        // when
        boolean lateInclusion = service.closeEligibleRevenue(REVENUE_ID, PERIOD_START, PERIOD_END);

        // then
        assertThat(lateInclusion).isFalse();
        assertThat(revenue.getStatus()).isEqualTo(OwnerRevenueStatus.ACCRUED);
        verify(weeklySettlementItemRepository, never()).save(any());
    }

    @Test
    void 이미_정산_대기로_넘어간_원장은_다시_포함하지_않는다() {
        // given — 잠그기 전에 다른 실행이 먼저 마감했다
        OwnerRevenue revenue = revenue(PERIOD_START.plusDays(2));
        revenue.markSettlementPending(PERIOD_END);
        givenLocked(revenue, settlement());

        // when
        boolean lateInclusion = service.closeEligibleRevenue(REVENUE_ID, PERIOD_START, PERIOD_END);

        // then
        assertThat(lateInclusion).isFalse();
        verify(weeklySettlementItemRepository, never()).save(any());
    }

    @Test
    void 정산_행을_잠그지_못하면_마감을_중단한다() {
        // given
        OwnerRevenue revenue = revenue(PERIOD_START.plusDays(2));
        when(ownerRevenueRepository.findById(REVENUE_ID)).thenReturn(Optional.of(revenue));
        when(weeklySettlementRepository.findByStoreAndPeriodWithPessimisticLock(STORE_ID, PERIOD_START, PERIOD_END))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.closeEligibleRevenue(REVENUE_ID, PERIOD_START, PERIOD_END))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_INVALID_STATUS);
    }

    // ─────────────────── 헬퍼 ───────────────────

    private void givenLocked(OwnerRevenue revenue, WeeklySettlement settlement) {
        when(ownerRevenueRepository.findById(REVENUE_ID)).thenReturn(Optional.of(revenue));
        when(weeklySettlementRepository.insertIfAbsent(anyLong(), any(), any(), anyString())).thenReturn(1);
        when(weeklySettlementRepository.findByStoreAndPeriodWithPessimisticLock(STORE_ID, PERIOD_START, PERIOD_END))
                .thenReturn(Optional.of(settlement));
        when(ownerRevenueRepository.findByIdWithPessimisticLock(REVENUE_ID)).thenReturn(Optional.of(revenue));
    }

    private WeeklySettlement settlement() {
        WeeklySettlement settlement = WeeklySettlement.create(
                store, PERIOD_START, PERIOD_END, "weekly:" + STORE_ID + ":" + PERIOD_END);
        ReflectionTestUtils.setField(settlement, "weeklySettlementId", 100L);
        return settlement;
    }

    private OwnerRevenue revenue(LocalDateTime settleableAt) {
        Order order = mock(Order.class);
        when(order.getOrderId()).thenReturn(1L);
        when(order.getStore()).thenReturn(store);

        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn(PaymentStatus.PAID);
        when(payment.getOrder()).thenReturn(order);
        when(payment.getAmount()).thenReturn(new BigDecimal("10000"));

        OwnerRevenue revenue = OwnerRevenue.create(order, payment,
                new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10000"));
        ReflectionTestUtils.setField(revenue, "ownerRevenueId", REVENUE_ID);
        ReflectionTestUtils.setField(revenue, "settleableAt", settleableAt);
        return revenue;
    }
}
