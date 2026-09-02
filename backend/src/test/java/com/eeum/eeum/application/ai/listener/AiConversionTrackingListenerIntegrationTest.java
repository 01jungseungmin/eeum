package com.eeum.eeum.application.ai.listener;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.ai.entity.AiConversionEvent;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.entity.AiMessageDelivery;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiConversionType;
import com.eeum.eeum.domain.ai.enums.AiDeliveryStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiConversionEventRepository;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.ai.repository.AiMessageDeliveryRepository;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.enums.OrderType;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.domain.notification.repository.NotificationSettingsRepository;
import com.eeum.eeum.domain.order.event.OrderPaidEvent;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * AI 전환 추적 리스너(@Async + AFTER_COMMIT) 통합 테스트 — 실제 MySQL/Redis Testcontainer 환경에서 실행.
 *
 * 단위 테스트로는 검증 불가한 지점만 다룬다:
 * - 트랜잭션 커밋 후에만 리스너가 실행되는지 (AFTER_COMMIT 의미론)
 * - 트랜잭션 롤백 시 리스너가 실행되지 않는지 — 이게 AFTER_COMMIT의 존재 이유
 * - 비동기 실행 + 7일 귀속 윈도우 쿼리(SentAtAfter)가 실제 DB에서 동작하는지
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class AiConversionTrackingListenerIntegrationTest extends IntegrationTestSupport {



    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final AiMessageDeliveryRepository aiMessageDeliveryRepository;
    private final AiConversionEventRepository aiConversionEventRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;

    private Long customerAccountId;
    private Long orderId;

    @BeforeEach
    void setUp() {
        Account owner = accountRepository.save(
                Account.createOwner("conv-owner@test.com", "encoded_pw", "점주", "010-1111-1111"));
        Store store = storeRepository.save(
                Store.createForOwnerSignup(owner, "전환 테스트 상점", "서울시", "02-0000-0000"));

        Account customer = accountRepository.save(Account.createUser(
                "conv-customer@test.com", "encoded_pw", "고객", "전환닉", "010-2222-2222"));
        customerAccountId = customer.getAccountId();

        // 7일 귀속 윈도우 안의 SENT 발송 이력 — 전환 귀속 대상
        AiGeneratedMessage message = AiGeneratedMessage.createDraft(
                store, owner, AiMessageType.EVENT_MARKETING, null, null,
                "이벤트", "내용", AiChannel.APP_PUSH);
        message.edit(null, null);
        message.send(LocalDateTime.now());
        message = aiGeneratedMessageRepository.save(message);
        aiMessageDeliveryRepository.save(AiMessageDelivery.record(
                message, store, customerAccountId, AiChannel.APP_PUSH,
                AiDeliveryStatus.SENT, null, "provider-msg-1"));

        Order order = Order.create(customer, store, BigDecimal.valueOf(10000),
                "CONV-TEST-ORD-1", OrderType.SALE, null, null);
        order.complete();
        orderId = orderRepository.save(order).getOrderId();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // OrderPaidEvent는 AI 전환 리스너 외에 알림(Notification) 리스너도 @Async로 구독한다 —
        // 비동기 알림 생성이 끝난 뒤 정리해야 account FK 위반 없이 삭제된다
        Thread.sleep(1500);
        notificationRepository.deleteAll();
        notificationSettingsRepository.deleteAll();
        aiConversionEventRepository.deleteAll();
        aiMessageDeliveryRepository.deleteAll();
        aiGeneratedMessageRepository.deleteAll();
        orderRepository.deleteAll();
        storeRepository.deleteAll();
    }

    private OrderPaidEvent orderPaidEvent() {
        return new OrderPaidEvent(customerAccountId, "전환 테스트 상점", "CONV-TEST-ORD-1",
                BigDecimal.valueOf(10000), orderId);
    }

    @Test
    void 트랜잭션_커밋_후_비동기_리스너가_발송_이력에_귀속된_전환을_기록한다() {
        // when — 실제 트랜잭션 안에서 이벤트 발행 후 정상 커밋
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(orderPaidEvent()));

        // then — @Async 리스너가 커밋 후 실행되므로 Awaitility로 대기
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<AiConversionEvent> conversions = aiConversionEventRepository.findAll();
            assertThat(conversions).hasSize(1);
            assertThat(conversions.get(0).getAccountId()).isEqualTo(customerAccountId);
            assertThat(conversions.get(0).getConversionType()).isEqualTo(AiConversionType.ORDER);
            assertThat(conversions.get(0).getOrderId()).isEqualTo(orderId);
        });
    }

    @Test
    void 트랜잭션이_롤백되면_리스너가_실행되지_않아_전환이_기록되지_않는다() {
        // when — 이벤트 발행 후 강제 롤백
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            eventPublisher.publishEvent(orderPaidEvent());
            throw new RuntimeException("강제 롤백");
        })).isInstanceOf(RuntimeException.class);

        // then — 3초간 지속적으로 전환이 생기지 않음을 확인 (AFTER_COMMIT 의미론)
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5))
                .until(() -> aiConversionEventRepository.count() == 0);
    }

    @Test
    void 같은_주문_이벤트가_중복_발행돼도_전환은_한_번만_기록된다() {
        // when — 커밋된 트랜잭션에서 같은 이벤트를 두 번 발행
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> eventPublisher.publishEvent(orderPaidEvent()));
        await().atMost(Duration.ofSeconds(10))
                .until(() -> aiConversionEventRepository.count() == 1);
        tx.executeWithoutResult(status -> eventPublisher.publishEvent(orderPaidEvent()));

        // then — 메시지×고객 중복 전환 방지 정책으로 1건 유지
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5))
                .until(() -> aiConversionEventRepository.count() == 1);
    }
}
