package com.eeum.eeum.application.settlement.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.settlement.entity.WeeklySettlement;
import com.eeum.eeum.domain.settlement.repository.WeeklySettlementRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ManualSettlementPayoutService {

    private final WeeklySettlementRepository weeklySettlementRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public void claim(Long adminAccountId, Long weeklySettlementId, String claimToken, LocalDateTime expiresAt) {
        Account admin = accountRepository.findById(adminAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (!admin.isAdmin()) {
            throw new BusinessException(ErrorCode.COMMON_FORBIDDEN);
        }
        WeeklySettlement settlement = weeklySettlementRepository.findByIdWithPessimisticLock(weeklySettlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        settlement.claim(claimToken, expiresAt, LocalDateTime.now(), LocalDateTime.now());
    }

    @Transactional
    public void complete(Long adminAccountId, Long weeklySettlementId, String claimToken, String payoutReference) {
        Account admin = accountRepository.findById(adminAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (!admin.isAdmin()) {
            throw new BusinessException(ErrorCode.COMMON_FORBIDDEN);
        }
        WeeklySettlement settlement = weeklySettlementRepository.findByIdWithPessimisticLock(weeklySettlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS));
        settlement.completeManually(admin, claimToken, payoutReference, LocalDateTime.now());
    }
}
