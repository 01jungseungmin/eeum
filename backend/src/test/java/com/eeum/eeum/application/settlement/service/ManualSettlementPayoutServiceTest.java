package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.application.settlement.dto.response.BlockingCancellationResponseDto;
import com.eeum.eeum.application.order.service.PaymentCancellationService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.PaymentCancellationOperation;
import com.eeum.eeum.domain.order.enums.PaymentCancellationStatus;
import com.eeum.eeum.domain.order.repository.PaymentCancellationOperationRepository;
import com.eeum.eeum.domain.settlement.entity.OwnerRevenue;
import com.eeum.eeum.domain.settlement.entity.WeeklySettlement;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementItemAmounts;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementItemRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualSettlementPayoutServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long SETTLEMENT_ID = 10L;
    private static final Long ORDER_ID = 100L;

    @Mock private WeeklySettlementRepository weeklySettlementRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private WeeklySettlementItemRepository weeklySettlementItemRepository;
    @Mock private OwnerRevenueRepository ownerRevenueRepository;
    @Mock private PaymentCancellationOperationRepository cancellationOperationRepository;
    @Mock private PaymentCancellationService paymentCancellationService;
    @Mock private Account admin;
    @Mock private WeeklySettlement settlement;

    private ManualSettlementPayoutService service;

    @BeforeEach
    void setUp() {
        service = new ManualSettlementPayoutService(
                weeklySettlementRepository,
                accountRepository,
                weeklySettlementItemRepository,
                ownerRevenueRepository,
                cancellationOperationRepository,
                paymentCancellationService);
    }

    @Test
    void 관리자가_아니면_지급을_claim_할_수_없다() {
        // given
        when(accountRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(admin.isAdmin()).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> service.claim(ADMIN_ID, SETTLEMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_FORBIDDEN);

        verify(weeklySettlementRepository, never()).findByIdWithPessimisticLock(any());
    }

    @Test
    void claim은_임대_토큰을_발급하고_정산을_지급중으로_바꾼다() {
        // given
        givenAdminAndSettlement();
        givenIncludedOrders();
        givenNoBlockingCancellation();

        // when
        String claimToken = service.claim(ADMIN_ID, SETTLEMENT_ID);

        // then
        assertThat(claimToken).isNotBlank();
        verify(settlement).claim(eq(admin), eq(claimToken), any(), any(), any());
    }

    @Test
    void 미완료_취소가_있으면_claim_단계에서_막힌다() {
        // given — 아직 마무리되지 않은 취소가 있는 주문이 정산에 포함돼 있다
        givenAdminAndSettlement();
        givenIncludedOrders();
        givenBlockingCancellation();

        // when / then
        assertThatThrownBy(() -> service.claim(ADMIN_ID, SETTLEMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_BLOCKED_BY_CANCELLATION);

        verify(settlement, never()).claim(any(), any(), any(), any(), any());
    }

    @Test
    void claim_뒤_부분취소_작업이_생기면_수동_지급을_완료할_수_없다() {
        // given
        givenAdminAndSettlement();
        givenIncludedOrders();
        givenBlockingCancellation();

        // when / then
        assertThatThrownBy(() -> service.complete(ADMIN_ID, SETTLEMENT_ID, "claim", "transfer-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_BLOCKED_BY_CANCELLATION);

        verify(settlement).validateManualCompletion(eq(admin), eq("claim"), eq("transfer-1"), any());
        verify(ownerRevenueRepository, never()).findAllByIdInWithPessimisticLock(any());
        verify(settlement, never()).completeManually(any(), any(), any(), any());
    }

    @Test
    void 정산_합계가_항목_합계와_어긋나면_지급하지_않는다() {
        // given — 증분으로 유지해 온 합계가 항목 원본과 다르다
        givenAdminAndSettlement();
        givenIncludedOrders();
        givenNoBlockingCancellation();
        givenItemAmounts();
        doThrow(new BusinessException(ErrorCode.SETTLEMENT_AMOUNT_MISMATCH))
                .when(settlement).reconcileWithItemAmounts(any(), any(), any(), any());

        // when / then
        assertThatThrownBy(() -> service.complete(ADMIN_ID, SETTLEMENT_ID, "claim", "transfer-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_AMOUNT_MISMATCH);

        verify(ownerRevenueRepository, never()).findAllByIdInWithPessimisticLock(any());
        verify(settlement, never()).completeManually(any(), any(), any(), any());
    }

    @Test
    void 지급이_확정되면_포함된_원장이_모두_SETTLED로_바뀐다() {
        // given
        givenAdminAndSettlement();
        givenIncludedOrders();
        givenNoBlockingCancellation();
        givenItemAmounts();
        OwnerRevenue first = org.mockito.Mockito.mock(OwnerRevenue.class);
        OwnerRevenue second = org.mockito.Mockito.mock(OwnerRevenue.class);
        when(weeklySettlementItemRepository.findOwnerRevenueIdsByWeeklySettlementId(SETTLEMENT_ID))
                .thenReturn(List.of(1L, 2L));
        when(ownerRevenueRepository.findAllByIdInWithPessimisticLock(List.of(1L, 2L)))
                .thenReturn(List.of(first, second));

        // when
        service.complete(ADMIN_ID, SETTLEMENT_ID, "claim", "transfer-1");

        // then
        verify(first).markSettled();
        verify(second).markSettled();
        verify(settlement).completeManually(eq(admin), eq("claim"), eq("transfer-1"), any());
    }

    @Test
    void 포함_원장을_모두_잠그지_못하면_지급을_중단한다() {
        // given — 마감·취소가 끼어들어 잠글 대상이 줄었다
        givenAdminAndSettlement();
        givenIncludedOrders();
        givenNoBlockingCancellation();
        givenItemAmounts();
        when(weeklySettlementItemRepository.findOwnerRevenueIdsByWeeklySettlementId(SETTLEMENT_ID))
                .thenReturn(List.of(1L, 2L));
        when(ownerRevenueRepository.findAllByIdInWithPessimisticLock(List.of(1L, 2L)))
                .thenReturn(List.of(org.mockito.Mockito.mock(OwnerRevenue.class)));

        // when / then
        assertThatThrownBy(() -> service.complete(ADMIN_ID, SETTLEMENT_ID, "claim", "transfer-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_CONCURRENT_MODIFICATION);

        verify(settlement, never()).completeManually(any(), any(), any(), any());
    }

    @Test
    void 지급을_막고_있는_취소_작업을_관리자가_조회할_수_있다() {
        // given — 차단 판단과 같은 조회를 그대로 노출해야 관리자가 해소할 수 있다
        when(accountRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(admin.isAdmin()).thenReturn(true);
        givenIncludedOrders();
        givenBlockingCancellation();

        // when
        List<BlockingCancellationResponseDto> blocking =
                service.getBlockingCancellations(ADMIN_ID, SETTLEMENT_ID);

        // then
        assertThat(blocking).hasSize(1);
        assertThat(blocking.get(0).orderId()).isEqualTo(ORDER_ID);
        assertThat(blocking.get(0).status()).isEqualTo(PaymentCancellationStatus.MANUAL_REVIEW_REQUIRED);
    }

    @Test
    void 정산에_포함된_주문의_확정된_취소만_내부_반영을_재시도한다() {
        // given
        when(accountRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(admin.isAdmin()).thenReturn(true);
        givenIncludedOrders();

        // when
        service.applyConfirmedCancellation(ADMIN_ID, SETTLEMENT_ID, ORDER_ID);

        // then
        verify(paymentCancellationService).applyConfirmedManualReviewCancellation(ORDER_ID);
    }

    @Test
    void 정산에_포함되지_않은_주문의_취소는_해소할_수_없다() {
        // given
        when(accountRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(admin.isAdmin()).thenReturn(true);
        when(weeklySettlementItemRepository.findOrderIdsByWeeklySettlementId(SETTLEMENT_ID))
                .thenReturn(List.of());

        // when / then
        assertThatThrownBy(() -> service.applyConfirmedCancellation(ADMIN_ID, SETTLEMENT_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_INVALID_STATUS);
        verify(paymentCancellationService, never()).applyConfirmedManualReviewCancellation(any());
    }

    // ─────────────────── 헬퍼 ───────────────────

    private void givenAdminAndSettlement() {
        when(accountRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(admin.isAdmin()).thenReturn(true);
        when(weeklySettlementRepository.findByIdWithPessimisticLock(SETTLEMENT_ID))
                .thenReturn(Optional.of(settlement));
    }

    private void givenIncludedOrders() {
        when(weeklySettlementItemRepository.findOrderIdsByWeeklySettlementId(SETTLEMENT_ID))
                .thenReturn(List.of(ORDER_ID));
    }

    private void givenNoBlockingCancellation() {
        when(cancellationOperationRepository.findUncompletedByOrderIds(
                List.of(ORDER_ID), PaymentCancellationStatus.COMPLETED))
                .thenReturn(List.of());
    }

    private void givenBlockingCancellation() {
        Order order = org.mockito.Mockito.mock(Order.class);
        when(order.getOrderId()).thenReturn(ORDER_ID);
        PaymentCancellationOperation operation = org.mockito.Mockito.mock(PaymentCancellationOperation.class);
        when(operation.getOrder()).thenReturn(order);
        when(operation.getStatus()).thenReturn(PaymentCancellationStatus.MANUAL_REVIEW_REQUIRED);
        when(cancellationOperationRepository.findUncompletedByOrderIds(
                List.of(ORDER_ID), PaymentCancellationStatus.COMPLETED))
                .thenReturn(List.of(operation));
    }

    private void givenItemAmounts() {
        when(weeklySettlementItemRepository.sumAmountsByWeeklySettlementId(SETTLEMENT_ID))
                .thenReturn(new WeeklySettlementItemAmounts(
                        new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10000")));
    }
}
