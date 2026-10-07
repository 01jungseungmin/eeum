package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.application.account.service.AccountService;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기기 푸시 토큰 소유 이전 회귀.
 *
 * 이전 계정에 토큰이 남으면 계정을 바꾼 기기로 이전 계정의 알림이 간다.
 * 한 문장으로 옮기는지는 실제 DB에서만 확인된다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class FcmTokenTransferIntegrationTest extends IntegrationTestSupport {

    private final AccountRepository accountRepository;
    private final AccountService accountService;

    @Test
    void 같은_토큰을_새_계정이_등록하면_이전_계정에서는_사라진다() {
        // given
        String key = UUID.randomUUID().toString();
        String deviceToken = "device-" + key;
        Account previous = accountRepository.save(
                Account.createUser(key + "-a@test.com", "pw", "이전", key + "a", "010-1111-1111"));
        Account next = accountRepository.save(
                Account.createUser(key + "-b@test.com", "pw", "다음", key + "b", "010-2222-2222"));
        previous.updateFcmToken(deviceToken);
        accountRepository.saveAndFlush(previous);

        // when
        accountService.updateFcmToken(next.getAccountId(), deviceToken);

        // then
        assertThat(accountRepository.findById(previous.getAccountId()).orElseThrow().getFcmToken())
                .isNull();
        assertThat(accountRepository.findById(next.getAccountId()).orElseThrow().getFcmToken())
                .isEqualTo(deviceToken);
    }

    @Test
    void 토큰을_비우면_자기_행만_지운다() {
        String key = UUID.randomUUID().toString();
        Account other = accountRepository.save(
                Account.createUser(key + "-c@test.com", "pw", "타인", key + "c", "010-3333-3333"));
        Account mine = accountRepository.save(
                Account.createUser(key + "-d@test.com", "pw", "본인", key + "d", "010-4444-4444"));
        other.updateFcmToken("device-other-" + key);
        mine.updateFcmToken("device-mine-" + key);
        accountRepository.saveAndFlush(other);
        accountRepository.saveAndFlush(mine);

        accountService.updateFcmToken(mine.getAccountId(), null);

        assertThat(accountRepository.findById(mine.getAccountId()).orElseThrow().getFcmToken()).isNull();
        assertThat(accountRepository.findById(other.getAccountId()).orElseThrow().getFcmToken())
                .isEqualTo("device-other-" + key);
    }

    @Test
    void 두_계정이_서로의_토큰을_동시에_등록해도_데드락없이_단일_소유권을_유지한다() throws Exception {
        String key = UUID.randomUUID().toString();
        String firstToken = "device-first-" + key;
        String secondToken = "device-second-" + key;
        Account first = accountRepository.save(
                Account.createUser(key + "-e@test.com", "pw", "첫째", key + "e", "010-5555-1111"));
        Account second = accountRepository.save(
                Account.createUser(key + "-f@test.com", "pw", "둘째", key + "f", "010-5555-2222"));
        first.updateFcmToken(firstToken);
        second.updateFcmToken(secondToken);
        accountRepository.saveAndFlush(first);
        accountRepository.saveAndFlush(second);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();

        Thread firstRequest = new Thread(() -> registerAfterStart(
                start, done, firstFailure, first.getAccountId(), secondToken));
        Thread secondRequest = new Thread(() -> registerAfterStart(
                start, done, secondFailure, second.getAccountId(), firstToken));
        firstRequest.start();
        secondRequest.start();
        start.countDown();

        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        assertOnlyRetryableLockFailure(firstFailure.get());
        assertOnlyRetryableLockFailure(secondFailure.get());
        assertThat(firstFailure.get() == null || secondFailure.get() == null)
                .as("동시 요청 중 하나는 토큰 이전을 완료해야 한다")
                .isTrue();

        long firstTokenOwners = accountRepository.findAll().stream()
                .filter(account -> firstToken.equals(account.getFcmToken()))
                .count();
        long secondTokenOwners = accountRepository.findAll().stream()
                .filter(account -> secondToken.equals(account.getFcmToken()))
                .count();
        assertThat(firstTokenOwners).isLessThanOrEqualTo(1);
        assertThat(secondTokenOwners).isLessThanOrEqualTo(1);
    }

    private void registerAfterStart(
            CountDownLatch start,
            CountDownLatch done,
            AtomicReference<Throwable> failure,
            Long accountId,
            String token
    ) {
        try {
            start.await();
            accountService.updateFcmToken(accountId, token);
        } catch (Throwable throwable) {
            failure.set(throwable);
        } finally {
            done.countDown();
        }
    }

    private void assertOnlyRetryableLockFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        assertThat(failure).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) failure).getErrorCode()).isEqualTo(ErrorCode.LOCK_ACQUIRE_FAILED);
    }
}
