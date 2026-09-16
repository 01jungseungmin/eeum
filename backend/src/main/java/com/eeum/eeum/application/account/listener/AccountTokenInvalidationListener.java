package com.eeum.eeum.application.account.listener;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 토큰 회수의 최종 근거 — 계정의 토큰 세대를 올린다.
 *
 * Redis 삭제만으로는 샌다: 비동기 풀 포화로 삭제가 버려지고, 진행 중인 재발급이 삭제 직후
 * 새 토큰을 저장하며, 인스턴스 간 Pub/Sub은 유실된다. 그래서 제재와 같은 트랜잭션에서
 * BEFORE_COMMIT으로 올린다 — AFTER_COMMIT은 트랜잭션이 이미 끝나 커밋되지 않는다.
 * 실패하면 원 트랜잭션도 롤백된다. 토큰을 회수 못 하면 제재가 성립하지 않는다.
 */
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
