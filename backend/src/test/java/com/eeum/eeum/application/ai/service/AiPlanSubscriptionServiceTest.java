package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiPlanSubscribeRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiPlanSubscribeResponseDto;
import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.application.order.service.PortOnePaymentClient;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.ai.entity.AiPlanPayment;
import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentStatus;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentRepository;
import com.eeum.eeum.domain.ai.repository.AiPlanSubscriptionRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPlanSubscriptionServiceTest {

    @InjectMocks
    private AiPlanSubscriptionService subscriptionService;

    @Mock private AiManagerSupportService supportService;
    @Mock private AiPlanPaymentRepository aiPlanPaymentRepository;
    @Mock private AiPlanSubscriptionRepository aiPlanSubscriptionRepository;
    @Mock private PortOnePaymentClient portOnePaymentClient;
    @Mock private RedisLockService redisLockService;

    private static final Long OWNER_ID = 100L;
    private static final Long STORE_ID = 1L;
    private static final String PAYMENT_ID = "ai-plan-1-abcd1234";

    private Store stubStore() {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(STORE_ID);
        lenient().when(supportService.getOwnerStore(OWNER_ID)).thenReturn(store);
        return store;
    }

    private void stubLockPassThrough() {
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return null;
        }).when(redisLockService).executeWithLock(anyString(), any(Duration.class), any(Runnable.class));
    }

    private AiPlanPayment pendingPayment(Store store, AiPlanType planType) {
        AiPlanPayment payment = AiPlanPayment.createPending(
                store, planType, planType.getMonthlyPrice(), PAYMENT_ID);
        ReflectionTestUtils.setField(payment, "aiPlanPaymentId", 1L);
        return payment;
    }

    private PortOnePaymentInfo portoneInfo(String status, BigDecimal amount) {
        PortOnePaymentInfo info = mock(PortOnePaymentInfo.class);
        lenient().when(info.getStatus()).thenReturn(status);
        lenient().when(info.getAmount()).thenReturn(amount);
        return info;
    }

    @Test
    void BASIC_플랜_구독_요청_시_PENDING_결제가_생성된다() {
        // given
        Store store = stubStore();
        when(aiPlanSubscriptionRepository.findFirstByStore_StoreIdAndActiveTrueOrderByCreatedAtDesc(STORE_ID))
                .thenReturn(Optional.empty());
        when(aiPlanPaymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        AiPlanSubscribeResponseDto response = subscriptionService.requestSubscription(
                OWNER_ID, new AiPlanSubscribeRequestDto(AiPlanType.BASIC));

        // then — 결제 성공 전이므로 구독은 생성되지 않는다
        assertThat(response.status()).isEqualTo(AiPlanPaymentStatus.PENDING);
        assertThat(response.paymentId()).startsWith("ai-plan-");
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("19000"));
        verify(aiPlanSubscriptionRepository, never()).save(any());
    }

    @Test
    void 이미_같은_플랜을_활성_구독_중이면_재결제_요청을_막는다() {
        // given — 이미 BASIC을 활성 구독 중인데 BASIC을 또 결제하려는 상황 (버그 리포트 재현)
        Store store = stubStore();
        AiPlanSubscription activeBasic = AiPlanSubscription.create(store, AiPlanType.BASIC, LocalDateTime.now());
        when(aiPlanSubscriptionRepository.findFirstByStore_StoreIdAndActiveTrueOrderByCreatedAtDesc(STORE_ID))
                .thenReturn(Optional.of(activeBasic));

        // when & then
        assertThatThrownBy(() -> subscriptionService.requestSubscription(
                OWNER_ID, new AiPlanSubscribeRequestDto(AiPlanType.BASIC)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_PLAN_ALREADY_SUBSCRIBED);
        verify(aiPlanPaymentRepository, never()).save(any());
    }

    @Test
    void 다른_플랜으로_업그레이드_요청은_허용된다() {
        // given — BASIC 활성 구독 중 PRO로 업그레이드 요청 (정상 플로우 — 막으면 안 됨)
        Store store = stubStore();
        AiPlanSubscription activeBasic = AiPlanSubscription.create(store, AiPlanType.BASIC, LocalDateTime.now());
        when(aiPlanSubscriptionRepository.findFirstByStore_StoreIdAndActiveTrueOrderByCreatedAtDesc(STORE_ID))
                .thenReturn(Optional.of(activeBasic));
        when(aiPlanPaymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        AiPlanSubscribeResponseDto response = subscriptionService.requestSubscription(
                OWNER_ID, new AiPlanSubscribeRequestDto(AiPlanType.PRO));

        // then
        assertThat(response.status()).isEqualTo(AiPlanPaymentStatus.PENDING);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("39000"));
    }

    @Test
    void FREE_플랜은_구독_결제를_요청할_수_없다() {
        // given
        stubStore();

        // when & then
        assertThatThrownBy(() -> subscriptionService.requestSubscription(
                OWNER_ID, new AiPlanSubscribeRequestDto(AiPlanType.FREE)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_INVALID_INPUT);
    }

    @Test
    void 결제_검증_성공_시_PRO_구독이_생성되고_기존_구독은_비활성화된다() {
        // given
        Store store = stubStore();
        stubLockPassThrough();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.PRO);
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        PortOnePaymentInfo paidInfo = portoneInfo("PAID", new BigDecimal("39000"));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(paidInfo);
        AiPlanSubscription oldSubscription = AiPlanSubscription.create(store, AiPlanType.BASIC, LocalDateTime.now());
        when(aiPlanSubscriptionRepository.findByStore_StoreIdAndActiveTrue(STORE_ID))
                .thenReturn(List.of(oldSubscription));

        // when
        AiPlanSubscribeResponseDto response = subscriptionService.completePayment(OWNER_ID, PAYMENT_ID);

        // then
        assertThat(response.status()).isEqualTo(AiPlanPaymentStatus.PAID);
        assertThat(oldSubscription.isActive()).isFalse();
        verify(aiPlanSubscriptionRepository).save(any(AiPlanSubscription.class));
    }

    @Test
    void 결제가_완료_상태가_아니면_플랜이_변경되지_않는다() {
        // given
        Store store = stubStore();
        stubLockPassThrough();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        PortOnePaymentInfo failedInfo = portoneInfo("FAILED", new BigDecimal("19000"));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(failedInfo);

        // when & then
        assertThatThrownBy(() -> subscriptionService.completePayment(OWNER_ID, PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_COMPLETED);
        verify(aiPlanSubscriptionRepository, never()).save(any());
    }

    @Test
    void 결제_금액이_불일치하면_PAYMENT_AMOUNT_MISMATCH_예외가_발생하고_플랜이_변경되지_않는다() {
        // given
        Store store = stubStore();
        stubLockPassThrough();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        PortOnePaymentInfo tamperedInfo = portoneInfo("PAID", new BigDecimal("100")); // 위변조 금액
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(tamperedInfo);

        // when & then
        assertThatThrownBy(() -> subscriptionService.completePayment(OWNER_ID, PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        assertThat(payment.getStatus()).isEqualTo(AiPlanPaymentStatus.FAILED);
        verify(aiPlanSubscriptionRepository, never()).save(any());
    }

    @Test
    void Webhook_중복_수신_시_구독은_한_번만_반영된다() {
        // given — 이미 PAID 처리된 결제
        Store store = stubStore();
        stubLockPassThrough();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        payment.markPaid(LocalDateTime.now());
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));

        // when
        subscriptionService.handleWebhook(PAYMENT_ID);

        // then — PortOne 재검증도, 구독 생성도 일어나지 않는다
        verify(portOnePaymentClient, never()).getPayment(anyString());
        verify(aiPlanSubscriptionRepository, never()).save(any());
    }

    @Test
    void 타_사장의_결제는_완료_처리할_수_없다() {
        // given — 결제의 store와 로그인 사장의 store가 다름
        stubStore();
        Store otherStore = mock(Store.class);
        when(otherStore.getStoreId()).thenReturn(999L);
        AiPlanPayment payment = pendingPayment(otherStore, AiPlanType.BASIC);
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> subscriptionService.completePayment(OWNER_ID, PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_FORBIDDEN);
    }

    @Test
    void 구독_취소_기본_정책은_기간_종료일까지_유지된다() {
        // given
        Store store = stubStore();
        AiPlanSubscription subscription = AiPlanSubscription.createWithPeriod(
                store, AiPlanType.BASIC, LocalDateTime.now(), LocalDateTime.now().plusMonths(1));
        when(aiPlanSubscriptionRepository.findFirstByStore_StoreIdAndActiveTrueOrderByCreatedAtDesc(STORE_ID))
                .thenReturn(Optional.of(subscription));

        // when — cancel-immediately 기본값 false
        subscriptionService.cancelSubscription(OWNER_ID);

        // then — 즉시 비활성화되지 않음 (만료 스케줄러가 처리)
        assertThat(subscription.isActive()).isTrue();
    }
}
