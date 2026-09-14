package com.eeum.eeum.application.account.listener;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 토큰 회수의 최종 근거 — 계정의 토큰 세대를 올린다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountTokenInvalidationListener {

    private final AccountRepository accountRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAccountTokenCleanup(AccountTokenCleanupEvent event) {
        if (event.accountId() == null || !event.deleteRefreshToken()) {
            return;
        }

        Account account = accountRepository.findById(event.accountId()).orElse(null);
        if (account == null) {
            log.warn("토큰 무효화 대상 계정 없음: accountId={}", event.accountId());
            return;
        }

        account.invalidateIssuedTokens();
        log.debug("토큰 세대 증가: accountId={}, version={}",
                event.accountId(), account.getTokenVersion());
    }
}
