package com.eeum.eeum.application.ai.service;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.application.ai.dto.request.AiLocalMatchConditionRequestDto;
import com.eeum.eeum.application.ai.dto.request.AiOwnerMetricInputRequestDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiExposureStatus;
import com.eeum.eeum.domain.ai.entity.AiOwnerMetricInput;
import com.eeum.eeum.domain.ai.entity.AiPlanPayment;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import com.eeum.eeum.domain.ai.enums.AiCustomerType;
import com.eeum.eeum.domain.ai.enums.AiMetricType;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentStatus;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiExposureStatusRepository;
import com.eeum.eeum.domain.ai.repository.AiOwnerMetricInputRepository;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentRepository;
import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.enums.OrderType;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI 매니저 쓰기 Executor 영속성 통합 테스트 — 실제 MySQL Testcontainer 환경에서 실행.
 *
 * 단위 테스트(Mock)로는 검증 불가한 지점만 다룬다:
 * - uk_ai_exposure_store / uk_ai_metric_store_type_month Unique 제약 하의 upsert 멱등성
 * - save() 호출 없는 dirty checking 변경이 커밋 후 실제 DB에 반영되는지 (반드시 재조회로 검증)
 * - 여러 계정(주문 고객/찜 고객)의 실데이터 기반 대상 고객수 집계 쿼리
 * - AiPlanPaymentFailureRecorder의 REQUIRES_NEW 분리 커밋이 호출부 롤백에도 살아남는지
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class AiExposureAndMetricPersistenceIntegrationTest extends IntegrationTestSupport {



    private final AiExposureCommandExecutor aiExposureCommandExecutor;
    private final AiOwnerMetricCommandExecutor aiOwnerMetricCommandExecutor;
    private final AiPlanPaymentFailureRecorder aiPlanPaymentFailureRecorder;
    private final PlatformTransactionManager transactionManager;

    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final FavoriteRepository favoriteRepository;
    private final AiExposureStatusRepository aiExposureStatusRepository;
    private final AiActionLogRepository aiActionLogRepository;
    private final AiOwnerMetricInputRepository aiOwnerMetricInputRepository;
    private final AiPlanPaymentRepository aiPlanPaymentRepository;

    private Long ownerAccountId;
    private Store store;

    @BeforeEach
    void setUp() {
        Account owner = accountRepository.save(
                Account.createOwner("ai-owner@test.com", "encoded_pw", "점주", "010-1111-1111"));
        ownerAccountId = owner.getAccountId();
        store = storeRepository.save(
                Store.createForOwnerSignup(owner, "AI 테스트 상점", "서울시", "02-0000-0000"));

        // 주문 완료 고객 1명 + 찜만 한 고객 1명 → 전체 고객 2, 신규(주문 이력 없음) 1
        Account orderCustomer = accountRepository.save(Account.createUser(
                "ai-order-customer@test.com", "encoded_pw", "주문고객", "주문닉", "010-2222-2222"));
        Order order = Order.create(orderCustomer, store, BigDecimal.valueOf(10000),
                "AI-TEST-ORD-1", OrderType.SALE, null, null);
        order.complete();
        orderRepository.save(order);

        Account favoriteCustomer = accountRepository.save(Account.createUser(
                "ai-favorite-customer@test.com", "encoded_pw", "찜고객", "찜닉", "010-3333-3333"));
        favoriteRepository.save(
                Favorite.create(favoriteCustomer, FavoriteRefType.STORE, store.getStoreId()));
    }

    @AfterEach
    void tearDown() {
        aiActionLogRepository.deleteAll();
        aiExposureStatusRepository.deleteAll();
        aiOwnerMetricInputRepository.deleteAll();
        aiPlanPaymentRepository.deleteAll();
        favoriteRepository.deleteAll();
        orderRepository.deleteAll();
        storeRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ──────────────────── AiExposureCommandExecutor ────────────────────

    @Test
    void 노출_시작_후_재조회하면_활성_상태와_실데이터_기반_대상_고객수가_DB에_반영된다() {
        // when
        aiExposureCommandExecutor.startExposureInTx(store, ownerAccountId);

        // then — 반환값이 아닌 DB 재조회로 검증 (주문 고객 1 + 찜 고객 1 = 2)
        AiExposureStatus persisted = aiExposureStatusRepository
                .findByStore_StoreId(store.getStoreId()).orElseThrow();
        assertThat(persisted.isActive()).isTrue();
        assertThat(persisted.getStartedAt()).isNotNull();
        assertThat(persisted.getTargetCount()).isEqualTo(2);

        assertThat(aiActionLogRepository.findAll())
                .anyMatch(log -> log.getActionType() == AiActionType.EXPOSURE_STARTED);
    }

    @Test
    void 노출_중지_후_재조회하면_비활성_상태가_DB에_반영된다() {
        // given
        aiExposureCommandExecutor.startExposureInTx(store, ownerAccountId);

        // when
        aiExposureCommandExecutor.stopExposureInTx(store, ownerAccountId);

        // then
        AiExposureStatus persisted = aiExposureStatusRepository
                .findByStore_StoreId(store.getStoreId()).orElseThrow();
        assertThat(persisted.isActive()).isFalse();
        assertThat(persisted.getStoppedAt()).isNotNull();
        assertThat(aiActionLogRepository.findAll())
                .anyMatch(log -> log.getActionType() == AiActionType.EXPOSURE_STOPPED);
    }

    @Test
    void 조건_변경을_반복해도_uk_ai_exposure_store_제약_하에_노출_상태_행은_하나만_유지된다() {
        // when — 상태가 없는 채로 두 번 연속 조건 변경 (getOrCreate 경로 2회 진입)
        aiExposureCommandExecutor.updateConditionsInTx(
                store, new AiLocalMatchConditionRequestDto(1.5, "한식", null));
        aiExposureCommandExecutor.updateConditionsInTx(
                store, new AiLocalMatchConditionRequestDto(3.0, null, AiCustomerType.NEW));

        // then — 행 1개, 마지막 값 반영, NEW = 전체(2) - 주문 고객(1) = 1
        List<AiExposureStatus> all = aiExposureStatusRepository.findAll();
        assertThat(all).hasSize(1);
        AiExposureStatus persisted = all.get(0);
        assertThat(persisted.getRadiusKm()).isEqualTo(3.0);
        assertThat(persisted.getInterest()).isEqualTo("한식"); // null이면 기존 값 유지
        assertThat(persisted.getCustomerType()).isEqualTo(AiCustomerType.NEW);
        assertThat(persisted.getTargetCount()).isEqualTo(1);
    }

    // ──────────────────── AiOwnerMetricCommandExecutor ────────────────────

    @Test
    void 같은_연월로_실측값을_두_번_입력하면_행은_하나이고_dirty_checking으로_값이_갱신된다() {
        // when — 두 번째 호출은 save 없이 updateValue만 수행하는 경로
        aiOwnerMetricCommandExecutor.upsertMetricInTx(store, ownerAccountId,
                new AiOwnerMetricInputRequestDto(
                        AiMetricType.MONTHLY_POWER_BILL, new BigDecimal("100000"), "2026-07"));
        aiOwnerMetricCommandExecutor.upsertMetricInTx(store, ownerAccountId,
                new AiOwnerMetricInputRequestDto(
                        AiMetricType.MONTHLY_POWER_BILL, new BigDecimal("120000"), "2026-07"));

        // then — uk_ai_metric_store_type_month 위반 없이 1행, 재조회 값은 갱신본
        List<AiOwnerMetricInput> all = aiOwnerMetricInputRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getValue()).isEqualByComparingTo("120000");
        assertThat(aiActionLogRepository.findAll())
                .filteredOn(log -> log.getActionType() == AiActionType.OWNER_METRIC_INPUT)
                .hasSize(2);
    }

    @Test
    void 다른_연월의_실측값은_별도_행으로_저장된다() {
        // when
        aiOwnerMetricCommandExecutor.upsertMetricInTx(store, ownerAccountId,
                new AiOwnerMetricInputRequestDto(
                        AiMetricType.MONTHLY_POWER_BILL, new BigDecimal("100000"), "2026-06"));
        aiOwnerMetricCommandExecutor.upsertMetricInTx(store, ownerAccountId,
                new AiOwnerMetricInputRequestDto(
                        AiMetricType.MONTHLY_POWER_BILL, new BigDecimal("110000"), "2026-07"));

        // then
        assertThat(aiOwnerMetricInputRepository.findAll()).hasSize(2);
    }

    // ──────────────────── AiPlanPaymentFailureRecorder (REQUIRES_NEW) ────────────────────

    @Test
    void FAILED_마킹은_호출부_트랜잭션이_롤백되어도_분리_커밋으로_유지된다() {
        // given
        AiPlanPayment payment = aiPlanPaymentRepository.save(AiPlanPayment.createPending(
                store, AiPlanType.BASIC, new BigDecimal("9900"), "ai-test-payment-1"));

        // when — 바깥 트랜잭션 안에서 markFailed 호출 후 강제 롤백
        TransactionTemplate outerTx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> outerTx.execute(status -> {
            aiPlanPaymentFailureRecorder.markFailed("ai-test-payment-1");
            throw new RuntimeException("호출부 강제 롤백");
        })).isInstanceOf(RuntimeException.class);

        // then — REQUIRES_NEW로 분리 커밋된 FAILED 마킹은 살아남아야 한다
        AiPlanPayment persisted = aiPlanPaymentRepository
                .findByPortonePaymentId("ai-test-payment-1").orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(AiPlanPaymentStatus.FAILED);
    }
}
