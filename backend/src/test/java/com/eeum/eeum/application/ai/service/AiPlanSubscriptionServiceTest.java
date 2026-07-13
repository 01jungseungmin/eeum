package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiPlanSubscribeRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiPlanSubscribeResponseDto;
import com.eeum.eeum.common.lock.LockKeys;
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

import java.time.Duration;
import java.time.LocalDateTime;
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

        // when
        AiPlanSubscribeResponseDto response = subscriptionService.completePayment(OWNER_ID, PAYMENT_ID);

        // then
        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        verify(redisLockService).executeWithLock(
                eq(LockKeys.aiPlanPayment(PAYMENT_ID)), any(Duration.class), any(Runnable.class));
        verify(paymentCommandExecutor).applyPaidSubscriptionInTx(PAYMENT_ID);
        // 소유권 확인 조회 1회 + Executor 실행 후 최신 상태 재조회 1회
        verify(aiPlanPaymentRepository, times(2)).findByPortonePaymentId(PAYMENT_ID);
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

        // when
        subscriptionService.handleWebhook(PAYMENT_ID);

        // then
        verify(redisLockService).executeWithLock(
                eq(LockKeys.aiPlanPayment(PAYMENT_ID)), any(Duration.class), any(Runnable.class));
        verify(paymentCommandExecutor).applyPaidSubscriptionInTx(PAYMENT_ID);
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
