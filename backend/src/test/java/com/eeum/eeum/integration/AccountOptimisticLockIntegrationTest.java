package com.eeum.eeum.integration;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Account 상태 제재와 일반 정보 수정 사이의 stale write를 실제 MySQL에서 검증한다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class AccountOptimisticLockIntegrationTest extends IntegrationTestSupport {

    private static final String FCM_TOKEN = "invalid-fcm-token";


    private final AccountRepository accountRepository;
    private final PlatformTransactionManager transactionManager;

    private Long accountId;

    @BeforeEach
    void setUp() {
        Account account = Account.createUser(
                "account-lock@test.com",
                "encoded-password",
                "낙관적락",
                "account_lock_user",
                "010-1111-2222"
        );
        account.updateFcmToken(FCM_TOKEN);
        accountId = accountRepository.saveAndFlush(account).getAccountId();
    }

    @AfterEach
    void tearDown() {
        accountRepository.deleteAll();
    }

    @Test
    void 계정_정지_후_늦게_flush된_stale_정보_수정은_거부된다() {
        // given: 바깥 트랜잭션이 ACTIVE 계정을 먼저 읽은다.
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        // when: 다른 트랜잭션이 SUSPENDED로 커밋한 뒤 stale 정보 수정이 늦게 flush된다.
        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            Account stale = accountRepository.findById(accountId).orElseThrow();

            requiresNew().executeWithoutResult(inner ->
                    accountRepository.findByIdWithLock(accountId).orElseThrow().suspend());

            stale.updateInfo("stale_nickname", null);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        // then: 먼저 커밋된 정지 상태가 보존된다.
        Account persisted = accountRepository.findById(accountId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(persisted.getNickname()).isEqualTo("account_lock_user");
        assertThat(persisted.getVersion()).isEqualTo(1L);
    }

    @Test
    void FCM_토큰_bulk_update도_version을_증가시켜_stale_write를_거부한다() {
        // given
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        // when: bulk update가 토큰을 지운 후 stale Account가 늦게 flush된다.
        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            Account stale = accountRepository.findById(accountId).orElseThrow();

            requiresNew().executeWithoutResult(inner -> {
                int updated = accountRepository.clearFcmTokenByFcmToken(FCM_TOKEN);
                assertThat(updated).isEqualTo(1);
            });

            stale.updateInfo("stale_nickname", null);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        // then: bulk update 결과와 version이 보존된다.
        Account persisted = accountRepository.findById(accountId).orElseThrow();
        assertThat(persisted.getFcmToken()).isNull();
        assertThat(persisted.getNickname()).isEqualTo("account_lock_user");
        assertThat(persisted.getVersion()).isEqualTo(1L);
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
