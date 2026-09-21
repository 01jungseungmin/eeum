package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.UUID;

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
        accountRepository.transferFcmToken(next.getAccountId(), deviceToken);

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

        accountRepository.transferFcmToken(mine.getAccountId(), null);

        assertThat(accountRepository.findById(mine.getAccountId()).orElseThrow().getFcmToken()).isNull();
        assertThat(accountRepository.findById(other.getAccountId()).orElseThrow().getFcmToken())
                .isEqualTo("device-other-" + key);
    }
}
