package com.eeum.eeum.application.report.service;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.report.enums.ReportAction;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportedAccountActionService {

    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long apply(ReportAction action, Long accountId) {
        return switch (action) {
            case WARN_AUTHOR -> warn(accountId);
            case SUSPEND_AUTHOR -> suspend(accountId);
            default -> throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        };
    }

    private Long warn(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE));
        validateActionTarget(account);
        return accountId;
    }

    private Long suspend(Long accountId) {
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE));
        validateActionTarget(account);

        account.suspend();
        eventPublisher.publishEvent(AccountTokenCleanupEvent.refreshOnly(accountId));
        return accountId;
    }

    private void validateActionTarget(Account account) {
        if (account.isAdmin()) {
            throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_ALLOWED);
        }
        if (account.isWithdrawn()) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }
    }
}
