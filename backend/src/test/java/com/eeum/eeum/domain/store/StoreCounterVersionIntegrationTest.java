package com.eeum.eeum.domain.store;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 찜 카운터 bulk UPDATE와 상점 수정의 경쟁 검증.
 *
 * <p>Store는 {@code @Version}을 쓴다. 카운터 bulk UPDATE가 version을 올리지 않으면,
 * 이전에 Store를 읽어둔 요청이 나중에 저장할 때 버전이 일치해 낙관적 락이 걸리지 않고
 * 방금 증감한 favoriteCount를 오래된 값으로 덮어쓴다.
 * 조용히 값이 사라지는 종류라 발생해도 알 수 없어, 실제 DB에서 경쟁을 재현해 고정한다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class StoreCounterVersionIntegrationTest extends IntegrationTestSupport {

    private final StoreRepository storeRepository;
    private final AccountRepository accountRepository;
    private final PlatformTransactionManager transactionManager;

    private Long storeId;

    @BeforeEach
    void setUp() {
        Account owner = accountRepository.save(Account.createUser(
                "owner@test.com", "encoded_pw", "사장", "사장닉", "010-2222-2222"));
        storeId = storeRepository.saveAndFlush(
                Store.createForOwnerSignup(owner, "이음 카페", "서울시 강남구", "010-3333-3333"))
                .getStoreId();
    }

    @AfterEach
    void tearDown() {
        storeRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void 찜_증가와_겹친_상점_수정은_카운터를_덮어쓰지_않고_충돌로_끝난다() {
        // given: 상점을 읽어둔 뒤(favoriteCount=0) 다른 트랜잭션에서 찜이 증가한다.
        // when & then: 커밋 시점에 버전이 어긋나 낙관적 락 예외가 난다.
        //              예외 대신 커밋되면 favoriteCount가 0으로 되돌아간다.
        assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    Store loaded = storeRepository.findById(storeId).orElseThrow();
                    assertThat(loaded.getFavoriteCount()).isZero();

                    incrementFavoriteCountInOtherTransaction();

                    loaded.updateBasicInfo("이름 변경", "서울시 강남구", "010-3333-3333", "설명");
                }))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // 찜 수는 그대로 남는다
        assertThat(storeRepository.findById(storeId).orElseThrow().getFavoriteCount()).isEqualTo(1);
    }

    @Test
    void 경쟁이_없으면_상점_수정은_그대로_저장된다() {
        // 위 조건이 정상 수정까지 막지 않는지 확인한다
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Store loaded = storeRepository.findById(storeId).orElseThrow();
            loaded.updateBasicInfo("이름 변경", "서울시 강남구", "010-3333-3333", "설명");
        });

        assertThat(storeRepository.findById(storeId).orElseThrow().getName()).isEqualTo("이름 변경");
    }

    // 별도 스레드 + 별도 트랜잭션으로 찜 카운트를 올린다.
    private void incrementFavoriteCountInOtherTransaction() {
        CompletableFuture.runAsync(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(
                        status -> storeRepository.incrementFavoriteCount(storeId))).join();
    }
}
