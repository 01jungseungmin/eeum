package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.order.enums.PaymentCancellationStatus;
import com.eeum.eeum.domain.order.repository.PaymentCancellationOperationRepository;
import com.eeum.eeum.domain.settlement.entity.WeeklySettlement;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementItemRepository;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualSettlementPayoutServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long SETTLEMENT_ID = 10L;

    @Mock private WeeklySettlementRepository weeklySettlementRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private WeeklySettlementItemRepository weeklySettlementItemRepository;
    @Mock private OwnerRevenueRepository ownerRevenueRepository;
    @Mock private PaymentCancellationOperationRepository cancellationOperationRepository;
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
                cancellationOperationRepository);

        when(accountRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(admin.isAdmin()).thenReturn(true);
        when(weeklySettlementRepository.findByIdWithPessimisticLock(SETTLEMENT_ID))
                .thenReturn(Optional.of(settlement));
    }

    @Test
    void claim_뒤_부분취소_작업이_생기면_수동_지급을_완료할_수_없다() {
        // given
        when(weeklySettlementItemRepository.findOrderIdsByWeeklySettlementId(SETTLEMENT_ID))
                .thenReturn(List.of(100L));
        when(cancellationOperationRepository.existsUncompletedByOrderIds(
                eq(List.of(100L)), eq(PaymentCancellationStatus.COMPLETED)))
                .thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.complete(ADMIN_ID, SETTLEMENT_ID, "claim", "transfer-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_INVALID_STATUS);

        verify(settlement).validateManualCompletion(eq(admin), eq("claim"), eq("transfer-1"), any());
        verify(ownerRevenueRepository, never()).findAllByIdInWithPessimisticLock(any());
        verify(settlement, never()).completeManually(any(), any(), any(), any());
    }
}
