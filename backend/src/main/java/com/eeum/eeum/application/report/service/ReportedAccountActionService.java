package com.eeum.eeum.application.report.service;

import com.eeum.eeum.application.account.service.AccountSanctionPolicy;
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
    private final AccountSanctionPolicy accountSanctionPolicy;
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
        // suspend와 같은 잠금을 쓴다. 잠그지 않으면 대상 상태 확인과 조치 사이에
        // 탈퇴·정지가 끼어들어 이미 사라진 계정에 경고가 기록된다.
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE));
        accountSanctionPolicy.validateWarnable(account);
        return accountId;
    }

    private Long suspend(Long accountId) {
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_AVAILABLE));
        // 잠금 후 정지 여부까지 확인한다. 이 검사가 없으면 이미 정지된 계정에 조치를 반복할 때마다
        // 토큰 정리 이벤트가 다시 발행되고, 직접 정지 API와 오류 계약도 갈린다.
        accountSanctionPolicy.validateSuspendable(account);

        account.suspend();
        // 직접 정지 API와 같은 범위로 회수한다 — 한쪽만 Refresh만 지우면 경로에 따라 구멍이 생긴다
        eventPublisher.publishEvent(AccountTokenCleanupEvent.allTokens(accountId));
        return accountId;
    }
}
