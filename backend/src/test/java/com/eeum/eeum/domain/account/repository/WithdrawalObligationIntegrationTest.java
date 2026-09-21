package com.eeum.eeum.domain.account.repository;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.entity.Payment;
import com.eeum.eeum.domain.order.enums.OrderType;
import com.eeum.eeum.domain.order.enums.PaymentMethod;
import com.eeum.eeum.domain.order.enums.PaymentStatus;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.order.repository.PaymentRepository;
import com.eeum.eeum.domain.settlement.entity.OwnerRevenue;
import com.eeum.eeum.domain.settlement.enums.OwnerRevenueStatus;
import com.eeum.eeum.domain.settlement.repository.OwnerRevenueRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정산 계좌 보존 판정을 실제 DB로 고정한다.
 *
 * 목으로는 QueryDSL 경로와 상태 조합을 검증하지 못한다 — 깊은 경로 NPE도 여기서만 드러났다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class WithdrawalObligationIntegrationTest extends IntegrationTestSupport {

    private final WithdrawalObligationRepository withdrawalObligationRepository;
    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OwnerRevenueRepository ownerRevenueRepository;

    private Long ownerId;
    private OwnerRevenue revenue;
    private Payment payment;
    private Order order;
    private Store store;

    @AfterEach
    void cleanup() {
        if (revenue != null) ownerRevenueRepository.delete(revenue);
        if (payment != null) paymentRepository.delete(payment);
        if (order != null) orderRepository.delete(order);
        if (store != null) storeRepository.delete(store);
        revenue = null;
        payment = null;
        order = null;
        store = null;
    }

    @Test
    void 지급이_끝난_결제만_남으면_정산_계좌를_보존하지_않는다() {
        // given — 지급이 끝나도 결제는 PAID로 남는다
        givenOrderWithRevenue();
        ReflectionTestUtils.setField(revenue, "status", OwnerRevenueStatus.SETTLED);
        ownerRevenueRepository.saveAndFlush(revenue);

        // when & then
        assertThat(withdrawalObligationRepository.requiresSettlementAccount(ownerId)).isFalse();
    }

    @Test
    void 원장이_아직_미지급이면_정산_계좌를_보존한다() {
        // given
        givenOrderWithRevenue();

        // when & then — 생성 직후 원장은 ACCRUED다
        assertThat(revenue.getStatus()).isEqualTo(OwnerRevenueStatus.ACCRUED);
        assertThat(withdrawalObligationRepository.requiresSettlementAccount(ownerId)).isTrue();
    }

    @Test
    void 진행_중인_결제가_있으면_정산_계좌를_보존한다() {
        // given — 아직 원장이 생기지 않은 결제. 지급 의무가 앞으로 생긴다
        givenOrder(PaymentStatus.PENDING);

        // when & then
        assertThat(withdrawalObligationRepository.requiresSettlementAccount(ownerId)).isTrue();
    }

    private void givenOrderWithRevenue() {
        givenOrder(PaymentStatus.PAID);
        BigDecimal amount = payment.getAmount();
        revenue = ownerRevenueRepository.saveAndFlush(OwnerRevenue.create(
                order, payment, amount, BigDecimal.ZERO, BigDecimal.ZERO, amount));
    }

    private void givenOrder(PaymentStatus status) {
        String key = UUID.randomUUID().toString();
        Account owner = accountRepository.save(
                Account.createOwner(key + "-owner@test.com", "encoded", "점주", "010-1111-1111"));
        Account buyer = accountRepository.save(
                Account.createUser(key + "-buyer@test.com", "encoded", "구매자", key, "010-2222-2222"));
        ownerId = owner.getAccountId();

        store = storeRepository.save(
                Store.createForOwnerSignup(owner, "정산 테스트 상점", "서울시", "02-0000-0000"));

        BigDecimal amount = BigDecimal.valueOf(10000);
        order = orderRepository.saveAndFlush(Order.create(
                buyer, store, amount, key.substring(0, 18), OrderType.SALE, null, null));
        payment = paymentRepository.saveAndFlush(Payment.create(
                order, buyer, "portone-" + key, key, amount, PaymentMethod.EASY_PAY, status));
    }
}
