package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.application.order.service.PortOnePaymentClient;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPlanPaymentCommandExecutorTest {

    @InjectMocks
    private AiPlanPaymentCommandExecutor executor;

    @Mock private AiPlanPaymentRepository aiPlanPaymentRepository;
    @Mock private AiPlanSubscriptionRepository aiPlanSubscriptionRepository;
    @Mock private PortOnePaymentClient portOnePaymentClient;
    @Mock private AiPlanPaymentFailureRecorder failureRecorder;

    private static final Long STORE_ID = 1L;
    private static final String PAYMENT_ID = "ai-plan-1-abcd1234";

    private Store stubStore() {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(STORE_ID);
        return store;
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

    // ──────────────────── requestSubscriptionInTx ────────────────────

    @Test
    void BASIC_플랜_구독_요청_시_PENDING_결제가_생성된다() {
        // given
        Store store = stubStore();
        when(aiPlanSubscriptionRepository.findFirstByStore_StoreIdAndActiveTrueOrderByCreatedAtDesc(STORE_ID))
                .thenReturn(Optional.empty());
        when(aiPlanPaymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        AiPlanPayment payment = executor.requestSubscriptionInTx(store, AiPlanType.BASIC);

        // then
        assertThat(payment.getStatus()).isEqualTo(AiPlanPaymentStatus.PENDING);
        assertThat(payment.getPortonePaymentId()).startsWith("ai-plan-");
        assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("9900"));
    }

    @Test
    void 이미_같은_플랜을_활성_구독_중이면_재결제_요청을_막는다() {
        // given — 이미 BASIC을 활성 구독 중인데 BASIC을 또 결제하려는 상황
        Store store = stubStore();
        AiPlanSubscription activeBasic = AiPlanSubscription.create(store, AiPlanType.BASIC, LocalDateTime.now());
        when(aiPlanSubscriptionRepository.findFirstByStore_StoreIdAndActiveTrueOrderByCreatedAtDesc(STORE_ID))
                .thenReturn(Optional.of(activeBasic));

        // when & then
        assertThatThrownBy(() -> executor.requestSubscriptionInTx(store, AiPlanType.BASIC))
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
        AiPlanPayment payment = executor.requestSubscriptionInTx(store, AiPlanType.PRO);

        // then
        assertThat(payment.getStatus()).isEqualTo(AiPlanPaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("19900"));
    }

    // ──────────────────── applyPaidSubscriptionInTx ────────────────────

    @Test
    void 결제_검증_성공_시_PRO_구독이_생성되고_기존_구독은_비활성화된다() {
        // given
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.PRO);
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        PortOnePaymentInfo paidInfo = portoneInfo("PAID", new BigDecimal("19900"));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(paidInfo);
        AiPlanSubscription oldSubscription = AiPlanSubscription.create(store, AiPlanType.BASIC, LocalDateTime.now());
        when(aiPlanSubscriptionRepository.findByStore_StoreIdAndActiveTrue(STORE_ID))
                .thenReturn(List.of(oldSubscription));

        // when
        executor.applyPaidSubscriptionInTx(PAYMENT_ID);

        // then
        assertThat(payment.getStatus()).isEqualTo(AiPlanPaymentStatus.PAID);
        assertThat(oldSubscription.isActive()).isFalse();
        verify(aiPlanSubscriptionRepository).save(any(AiPlanSubscription.class));
    }

    @Test
    void 결제가_완료_상태가_아니면_플랜이_변경되지_않는다() {
        // given
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        PortOnePaymentInfo failedInfo = portoneInfo("FAILED", new BigDecimal("9900"));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(failedInfo);

        // when & then
        assertThatThrownBy(() -> executor.applyPaidSubscriptionInTx(PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_COMPLETED);
        verify(aiPlanSubscriptionRepository, never()).save(any());
    }

    @Test
    void 결제_금액이_불일치하면_PAYMENT_AMOUNT_MISMATCH_예외가_발생하고_FAILED_기록은_REQUIRES_NEW_빈에_위임된다() {
        // given
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        PortOnePaymentInfo tamperedInfo = portoneInfo("PAID", new BigDecimal("100")); // 위변조 금액
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(tamperedInfo);

        // when & then
        assertThatThrownBy(() -> executor.applyPaidSubscriptionInTx(PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        // 이 메서드가 예외로 롤백돼도 FAILED 마킹이 남도록 REQUIRES_NEW 빈에 위임했는지 확인
        verify(failureRecorder).markFailed(PAYMENT_ID);
        verify(aiPlanSubscriptionRepository, never()).save(any());
    }

    @Test
    void Webhook_중복_수신_시_구독은_한_번만_반영된다() {
        // given — 이미 PAID 처리된 결제 + 이미 매칭되는 활성 구독도 존재 (정상 케이스)
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        payment.markPaid(LocalDateTime.now());
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        AiPlanSubscription existingSubscription = AiPlanSubscription.create(store, AiPlanType.BASIC, LocalDateTime.now());
        when(aiPlanSubscriptionRepository.findByStore_StoreIdAndActiveTrue(STORE_ID))
                .thenReturn(List.of(existingSubscription));

        // when
        executor.applyPaidSubscriptionInTx(PAYMENT_ID);

        // then — PortOne 재검증도, 구독 생성도 일어나지 않는다
        verify(portOnePaymentClient, never()).getPayment(anyString());
        verify(aiPlanSubscriptionRepository, never()).save(any());
    }

    @Test
    void 결제는_PAID인데_구독이_누락되면_복구된다() {
        // given — payment.isPaid()=true 이지만 해당 플랜의 활성 구독이 전혀 없는 장애/버그 상황 재현
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        payment.markPaid(LocalDateTime.now());
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(aiPlanSubscriptionRepository.findByStore_StoreIdAndActiveTrue(STORE_ID))
                .thenReturn(List.of());

        // when
        executor.applyPaidSubscriptionInTx(PAYMENT_ID);

        // then — 결제 재검증(PortOne) 없이도 누락된 구독이 복구 생성된다
        verify(portOnePaymentClient, never()).getPayment(anyString());
        verify(aiPlanSubscriptionRepository).save(any(AiPlanSubscription.class));
    }
}
