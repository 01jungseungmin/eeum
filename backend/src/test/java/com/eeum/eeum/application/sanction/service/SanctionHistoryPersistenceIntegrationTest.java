package com.eeum.eeum.application.sanction.service;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.application.account.service.AdminAccountService;
import com.eeum.eeum.application.store.service.AdminStoreService;
import com.eeum.eeum.EeumApplication;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.enums.AccountStatus;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.sanction.enums.SanctionAction;
import com.eeum.eeum.domain.sanction.enums.SanctionSource;
import com.eeum.eeum.domain.sanction.enums.SanctionTargetType;
import com.eeum.eeum.domain.sanction.repository.SanctionHistoryRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfDockerAvailable
@SpringBootTest(classes = EeumApplication.class)
@RequiredArgsConstructor
class SanctionHistoryPersistenceIntegrationTest extends IntegrationTestSupport {

    private static final Long ADMIN_ID = 999L;


    private final AdminAccountService adminAccountService;
    private final AdminStoreService adminStoreService;
    private final SanctionHistoryService sanctionHistoryService;
    private final SanctionHistoryRepository sanctionHistoryRepository;
    private final StoreRepository storeRepository;
    private final AccountRepository accountRepository;
    private final TransactionTemplate transactionTemplate;

    private Long accountId;
    private Long storeId;

    @BeforeEach
    void setUp() {
        Account account = accountRepository.save(Account.createUser(
                "sanction-target@test.com",
                "encoded_pw",
                "제재 대상",
                "제재대상닉",
                "010-1111-1111"
        ));
        accountId = account.getAccountId();

        Account owner = accountRepository.save(Account.createOwner(
                "sanction-owner@test.com",
                "encoded_pw",
                "상점 소유자",
                "010-2222-2222"
        ));
        Store store = storeRepository.save(Store.createForOwnerSignup(
                owner,
                "제재 대상 상점",
                "서울시 강남구",
                "02-1111-1111"
        ));
        storeId = store.getStoreId();
    }

    @AfterEach
    void tearDown() {
        sanctionHistoryRepository.deleteAll();
        storeRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void 회원_정지와_해제는_상태와_제재_이력을_같이_저장하고_최신순으로_조회한다() {
        // Given & When
        adminAccountService.suspendAccount(ADMIN_ID, accountId);
        adminAccountService.activateAccount(ADMIN_ID, accountId);

        // Then
        Account storedAccount = accountRepository.findById(accountId).orElseThrow();
        assertThat(storedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        var histories = sanctionHistoryService.getAccountHistories(
                accountId,
                PageRequest.of(0, 20)
        );
        assertThat(histories.getTotalElements()).isEqualTo(2);
        assertThat(histories.getContent())
                .extracting(history -> history.getAction())
                .containsExactly(SanctionAction.ACTIVATE, SanctionAction.SUSPEND);
        assertThat(histories.getContent())
                .allSatisfy(history -> {
                    assertThat(history.getTargetType()).isEqualTo(SanctionTargetType.ACCOUNT);
                    assertThat(history.getTargetId()).isEqualTo(accountId);
                    assertThat(history.getSource()).isEqualTo(SanctionSource.DIRECT_ADMIN);
                    assertThat(history.getProcessedByAdminId()).isEqualTo(ADMIN_ID);
                    assertThat(history.getCreatedAt()).isNotNull();
                });
    }

    @Test
    void 상점_정지와_해제는_회원_이력과_섞이지_않는다() {
        // Given & When
        adminAccountService.suspendAccount(ADMIN_ID, accountId);
        adminStoreService.suspendStore(ADMIN_ID, storeId);
        adminStoreService.activateStore(ADMIN_ID, storeId);

        // Then
        Store storedStore = storeRepository.findById(storeId).orElseThrow();
        assertThat(storedStore.getStatus()).isEqualTo(StoreStatus.TEMP_CLOSED);

        var histories = sanctionHistoryService.getStoreHistories(
                storeId,
                PageRequest.of(0, 20)
        );
        assertThat(histories.getTotalElements()).isEqualTo(2);
        assertThat(histories.getContent())
                .extracting(history -> history.getAction())
                .containsExactly(SanctionAction.ACTIVATE, SanctionAction.SUSPEND);
        assertThat(histories.getContent())
                .allSatisfy(history -> {
                    assertThat(history.getTargetType()).isEqualTo(SanctionTargetType.STORE);
                    assertThat(history.getTargetId()).isEqualTo(storeId);
                });
    }

    @Test
    void 외부_트랜잭션이_실패하면_회원_정지와_제재_이력이_함께_롤백된다() {
        // Given & When
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            adminAccountService.suspendAccount(ADMIN_ID, accountId);
            throw new IllegalStateException("후속 처리 실패");
        })).isInstanceOf(IllegalStateException.class);

        // Then
        Account storedAccount = accountRepository.findById(accountId).orElseThrow();
        assertThat(storedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(sanctionHistoryRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtDescSanctionHistoryIdDesc(
                        SanctionTargetType.ACCOUNT,
                        accountId,
                        PageRequest.of(0, 20)
                )).isEmpty();
    }
}
