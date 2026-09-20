package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiPlanSubscribeRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiPlanSubscribeResponseDto;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.application.order.service.PortOnePaymentClient;
import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.application.order.dto.response.PortOneCancelResult;
import com.eeum.eeum.application.ai.dto.response.AiPlanPaymentCancellationPlan;
import com.eeum.eeum.application.operation.service.OperationFailureRecorder;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 이 클래스는 락 위임/소유권 검증만 다룬다 — 결제 검증/구독 반영의 실제 비즈니스 로직은
 * AiPlanPaymentCommandExecutorTest에서 검증한다 (락-TX 순서 리팩터링으로 로직이 그쪽으로 이동함).
 */
@ExtendWith(MockitoExtension.class)
class AiPlanSubscriptionServiceTest {

    @InjectMocks
    private AiPlanSubscriptionService subscriptionService;

    @Mock private AiManagerSupportService supportService;
    @Mock private AiPlanPaymentRepository aiPlanPaymentRepository;
    @Mock private AiPlanSubscriptionRepository aiPlanSubscriptionRepository;
    @Mock private AiPlanPaymentCommandExecutor paymentCommandExecutor;
    @Mock private RedisLockService redisLockService;
    @Mock private PortOnePaymentClient portOnePaymentClient;
    @Mock private OperationFailureRecorder operationFailureRecorder;

    private static final Long OWNER_ID = 100L;
    private static final Long STORE_ID = 1L;
    private static final String PAYMENT_ID = "ai-plan-1-abcd1234";

    private Store stubStore() {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(STORE_ID);
        lenient().when(supportService.getOwnerStore(OWNER_ID)).thenReturn(store);
        return store;
    }

    @SuppressWarnings("unchecked")
    private void stubSupplierLockPassThrough() {
        when(redisLockService.executeWithLock(anyString(), any(Duration.class), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(2)).get());
    }

    private void stubRunnableLockPassThrough() {
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

    @Test
    void FREE_플랜은_구독_결제를_요청할_수_없다() {
        // given
        stubStore();

        // when & then — Executor 호출 전에 걸러진다
        assertThatThrownBy(() -> subscriptionService.requestSubscription(
                OWNER_ID, new AiPlanSubscribeRequestDto(AiPlanType.FREE)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_INVALID_INPUT);
        verifyNoInteractions(paymentCommandExecutor);
    }

    @Test
    void 구독_요청은_store_단위_락_안에서_Executor에_위임된다() {
        // given
        Store store = stubStore();
        stubSupplierLockPassThrough();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        when(paymentCommandExecutor.requestSubscriptionInTx(store, AiPlanType.BASIC)).thenReturn(payment);

        // when
        AiPlanSubscribeResponseDto response = subscriptionService.requestSubscription(
                OWNER_ID, new AiPlanSubscribeRequestDto(AiPlanType.BASIC));

        // then
        assertThat(response.status()).isEqualTo(AiPlanPaymentStatus.PENDING);
        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        verify(redisLockService).executeWithLock(
                eq(LockKeys.aiPlanSubscriptionRequest(STORE_ID)), any(Duration.class), any(Supplier.class));
        verify(paymentCommandExecutor).requestSubscriptionInTx(store, AiPlanType.BASIC);
    }

    @Test
    void Executor가_던진_AI_PLAN_ALREADY_SUBSCRIBED_예외가_그대로_전파된다() {
        // given — 락으로 직렬화되는 동시 요청 중 하나가 이미 활성 구독을 감지한 상황
        Store store = stubStore();
        stubSupplierLockPassThrough();
        when(paymentCommandExecutor.requestSubscriptionInTx(store, AiPlanType.BASIC))
                .thenThrow(new BusinessException(ErrorCode.AI_PLAN_ALREADY_SUBSCRIBED));

        // when & then
        assertThatThrownBy(() -> subscriptionService.requestSubscription(
                OWNER_ID, new AiPlanSubscribeRequestDto(AiPlanType.BASIC)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_PLAN_ALREADY_SUBSCRIBED);
    }

    @Test
    void 결제_완료_검증_시_paymentId_락_안에서_Executor에_위임되고_최신_상태를_다시_조회한다() {
        // given
        Store store = stubStore();
        stubRunnableLockPassThrough();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(PortOnePaymentInfo.builder()
                .paymentId(PAYMENT_ID).status("PAID").amount(payment.getAmount()).build());

        // when
        AiPlanSubscribeResponseDto response = subscriptionService.completePayment(OWNER_ID, PAYMENT_ID);

        // then
        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        verify(redisLockService).executeWithLock(
                eq(LockKeys.aiPlanPayment(PAYMENT_ID)), any(Duration.class), any(Runnable.class));
        verify(paymentCommandExecutor).applyPaidSubscriptionInTx(eq(PAYMENT_ID), any(PortOnePaymentInfo.class));
        // 소유권 확인 1회 + 외부 검증 응답의 금액 대조 1회 + 최신 상태 재조회 1회
        verify(aiPlanPaymentRepository, times(3)).findByPortonePaymentId(PAYMENT_ID);
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
        verifyNoInteractions(paymentCommandExecutor);
    }

    @Test
    void 등록되지_않은_결제의_Webhook은_무시된다() {
        // given
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.empty());

        // when
        subscriptionService.handleWebhook(PAYMENT_ID);

        // then
        verifyNoInteractions(paymentCommandExecutor);
    }

    @Test
    void Webhook은_paymentId_락_안에서_Executor에_위임된다() {
        // given
        Store store = stubStore();
        stubRunnableLockPassThrough();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(PortOnePaymentInfo.builder()
                .paymentId(PAYMENT_ID).status("PAID").amount(payment.getAmount()).build());

        // when
        subscriptionService.handleWebhook(PAYMENT_ID);

        // then
        verify(redisLockService).executeWithLock(
                eq(LockKeys.aiPlanPayment(PAYMENT_ID)), any(Duration.class), any(Runnable.class));
        verify(paymentCommandExecutor).applyPaidSubscriptionInTx(eq(PAYMENT_ID), any(PortOnePaymentInfo.class));
    }

    @Test
    void AI_부분_취소_Webhook은_유료_권한을_중지하고_운영_이력에_남긴다() {
        // given
        Store store = stubStore();
        stubRunnableLockPassThrough();
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID))
                .thenReturn(Optional.of(pendingPayment(store, AiPlanType.BASIC)));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(PortOnePaymentInfo.builder()
                .paymentId(PAYMENT_ID).status("PARTIAL_CANCELLED").cancelledAmount(new BigDecimal("1000")).build());

        // when
        subscriptionService.handleWebhook(PAYMENT_ID);

        // then
        verify(paymentCommandExecutor).partiallyCancelPaidSubscriptionInTx(PAYMENT_ID);
        verify(operationFailureRecorder).record(
                eq(com.eeum.eeum.domain.operation.enums.OperationFailureCategory.REFUND),
                eq("AiPlanSubscriptionService.handleWebhook"), eq("PAYMENT"), eq(PAYMENT_ID),
                eq("AI_PLAN_PARTIAL_CANCELLATION_ACCESS_SUSPENDED"), anyString(), anyString());
    }

    @Test
    void AI_금액_불일치_환불의_REQUESTED_응답을_영속_작업과_운영_이력에_남긴다() {
        Store store = stubStore();
        stubRunnableLockPassThrough();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        when(aiPlanPaymentRepository.findByPortonePaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(PortOnePaymentInfo.builder()
                .paymentId(PAYMENT_ID).status("PAID").amount(new BigDecimal("100")).build());
        AiPlanPaymentCancellationPlan plan = new AiPlanPaymentCancellationPlan(
                PAYMENT_ID, new BigDecimal("100"), "ai-plan-refund-1", true);
        when(paymentCommandExecutor.prepareMismatchedPaymentCancellation(PAYMENT_ID, new BigDecimal("100")))
                .thenReturn(plan);
        when(portOnePaymentClient.cancelPayment(PAYMENT_ID, new BigDecimal("100"),
                "AI 플랜 결제 금액 불일치 — 자동 환불", "ai-plan-refund-1"))
                .thenReturn(new PortOneCancelResult(PortOneCancelResult.REQUESTED, "cancel-1", null));

        subscriptionService.handleWebhook(PAYMENT_ID);

        verify(paymentCommandExecutor).recordMismatchedPaymentCancellationResult(
                eq(PAYMENT_ID), any(PortOneCancelResult.class));
        verify(operationFailureRecorder).record(
                eq(com.eeum.eeum.domain.operation.enums.OperationFailureCategory.REFUND),
                eq("AiPlanSubscriptionService.autoCancel"), eq("PAYMENT"), eq(PAYMENT_ID),
                eq("AI_PLAN_REFUND_REQUESTED"), anyString(), anyString());
    }

    @Test
    void Webhook이_유실된_REQUESTED_환불은_PortOne_조회로_구독과_함께_확정한다() {
        // given
        stubRunnableLockPassThrough();
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(PortOnePaymentInfo.builder()
                .paymentId(PAYMENT_ID).status("CANCELLED").build());

        // when
        subscriptionService.reconcileRequestedMismatchedPaymentCancellation(PAYMENT_ID);

        // then
        verify(paymentCommandExecutor).confirmMismatchedPaymentCancellation(PAYMENT_ID);
        verify(paymentCommandExecutor).cancelPaidSubscriptionInTx(PAYMENT_ID);
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
