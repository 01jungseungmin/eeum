package com.eeum.eeum.application.account.service;

import com.eeum.eeum.domain.account.repository.WithdrawalObligationRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountWithdrawalGuard {
    private final WithdrawalObligationRepository obligations;

    // 호출자는 Account와 상점 잠금을 잡아 신규 거래를 차단한 상태여야 한다.
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public void assertSelfWithdrawalAllowed(Long accountId) {
        if (obligations.hasPendingOrders(accountId)
                || obligations.hasPendingReservations(accountId)
                || obligations.hasPendingSettlements(accountId)) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWAL_PENDING_TRANSACTION);
        }
    }
}
