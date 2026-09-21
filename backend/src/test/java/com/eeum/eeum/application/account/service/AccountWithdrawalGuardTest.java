package com.eeum.eeum.application.account.service;

import com.eeum.eeum.domain.account.repository.WithdrawalObligationRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountWithdrawalGuardTest {
    @Mock private WithdrawalObligationRepository obligations;
    @InjectMocks private AccountWithdrawalGuard guard;

    @Test
    void 진행중인_주문이_있으면_본인_탈퇴를_차단한다() {
        when(obligations.hasPendingOrders(1L)).thenReturn(true);

        assertThatThrownBy(() -> guard.assertSelfWithdrawalAllowed(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWAL_PENDING_TRANSACTION);
        verify(obligations, never()).hasPendingReservations(anyLong());
    }

    @Test
    void 주문이_없어도_예약_또는_정산이_남으면_차단한다() {
        when(obligations.hasPendingReservations(1L)).thenReturn(true);

        assertThatThrownBy(() -> guard.assertSelfWithdrawalAllowed(1L))
                .isInstanceOf(BusinessException.class);

        reset(obligations);
        when(obligations.hasPendingSettlements(1L)).thenReturn(true);
        assertThatThrownBy(() -> guard.assertSelfWithdrawalAllowed(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 모든_거래가_끝난_경우에만_탈퇴를_허용한다() {
        guard.assertSelfWithdrawalAllowed(1L);
        verify(obligations).hasPendingOrders(1L);
        verify(obligations).hasPendingReservations(1L);
        verify(obligations).hasPendingSettlements(1L);
    }
}
