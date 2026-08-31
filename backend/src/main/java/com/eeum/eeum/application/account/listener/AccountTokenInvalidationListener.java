package com.eeum.eeum.application.account.listener;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.event.AccountTokenCleanupEvent;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * 토큰 회수의 최종 근거를 계정에 남긴다.
 *
 * <p>Redis 삭제만으로는 회수가 보장되지 않는다. 세 가지 경로로 새어나간다 —
 * 비동기 풀이 포화되면 삭제 작업이 버려지고, 진행 중인 재발급이 삭제 직후 새 토큰을 저장하며,
 * 인스턴스 간 종료 신호(Pub/Sub)는 유실될 수 있다.
 *
 * <p>그래서 무효화 시각을 <b>제재와 같은 트랜잭션</b>에서 기록한다.
 * {@code BEFORE_COMMIT}이라 이 변경은 원 트랜잭션과 함께 커밋된다 — 비동기도 아니고
 * 별도 커넥션도 쓰지 않으므로 위 세 경로 어디에도 걸리지 않는다.
 *
 * <p>{@code AFTER_COMMIT}이면 안 된다. 그 시점에는 트랜잭션이 이미 끝나 변경이 커밋되지 못한다.
 *
 * <p>이 리스너가 실패하면 원 트랜잭션도 롤백된다. 의도된 것이다 —
 * 토큰을 회수할 수 없다면 제재 자체가 성립하지 않는다.
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

        account.invalidateTokensBefore(LocalDateTime.now());
        log.debug("토큰 무효화 시각 기록: accountId={}", event.accountId());
    }
}
