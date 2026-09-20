package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.order.dto.response.PortOnePaymentInfo;
import com.eeum.eeum.application.ai.dto.response.AiPlanPaymentCancellationPlan;
import com.eeum.eeum.domain.ai.entity.AiPlanPayment;
import com.eeum.eeum.domain.ai.entity.AiPlanPaymentCancellationOperation;
import com.eeum.eeum.domain.ai.entity.AiPlanSubscription;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentCancellationStatus;
import com.eeum.eeum.domain.ai.enums.AiPlanPaymentStatus;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentCancellationOperationRepository;
import com.eeum.eeum.domain.ai.repository.AiPlanPaymentRepository;
import com.eeum.eeum.domain.ai.repository.AiPlanSubscriptionRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
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
    @Mock private AiPlanPaymentCancellationOperationRepository cancellationOperationRepository;
    @Mock private AiPlanSubscriptionRepository aiPlanSubscriptionRepository;
    @Mock private StoreRepository storeRepository;

    private static final Long STORE_ID = 1L;
    private static final String PAYMENT_ID = "ai-plan-1-abcd1234";

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(aiPlanSubscriptionRepository.findByPayment_AiPlanPaymentId(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Optional.empty());
        lenient().when(storeRepository.findByIdWithPessimisticLock(STORE_ID))
                .thenAnswer(invocation -> Optional.of(stubStore()));
    }

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
        when(aiPlanPaymentRepository.findFirstByStore_StoreIdAndPlanTypeAndStatusOrderByCreatedAtDesc(
                STORE_ID, AiPlanType.BASIC, AiPlanPaymentStatus.PENDING))
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
    void 같은_플랜을_활성_구독_중이어도_남은_기간을_연장할_결제_요청을_허용한다() {
        // given — BASIC이 남아 있는 상태의 BASIC 연장 결제
        Store store = stubStore();
        when(aiPlanPaymentRepository.findFirstByStore_StoreIdAndPlanTypeAndStatusOrderByCreatedAtDesc(
                STORE_ID, AiPlanType.BASIC, AiPlanPaymentStatus.PENDING))
                .thenReturn(Optional.empty());
        when(aiPlanPaymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AiPlanPayment payment = executor.requestSubscriptionInTx(store, AiPlanType.BASIC);

        assertThat(payment.getStatus()).isEqualTo(AiPlanPaymentStatus.PENDING);
        verify(aiPlanPaymentRepository).save(any());
    }

    @Test
    void 아직_완료되지_않은_같은_플랜_결제는_재사용한다() {
        Store store = stubStore();
        AiPlanPayment pending = pendingPayment(store, AiPlanType.PRO);
        when(aiPlanPaymentRepository.findFirstByStore_StoreIdAndPlanTypeAndStatusOrderByCreatedAtDesc(
                STORE_ID, AiPlanType.PRO, AiPlanPaymentStatus.PENDING))
                .thenReturn(Optional.of(pending));

        AiPlanPayment payment = executor.requestSubscriptionInTx(store, AiPlanType.PRO);

        assertThat(payment).isSameAs(pending);
        verify(aiPlanPaymentRepository, never()).save(any());
    }

    // ──────────────────── applyPaidSubscriptionInTx ────────────────────

    @Test
    void 결제_검증_성공_시_PRO_구독이_생성되고_기존_구독은_비활성화된다() {
        // given
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.PRO);
        when(aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
        PortOnePaymentInfo paidInfo = portoneInfo("PAID", new BigDecimal("19900"));
        AiPlanSubscription oldSubscription = AiPlanSubscription.create(store, AiPlanType.BASIC, LocalDateTime.now());
        when(aiPlanSubscriptionRepository.findByStore_StoreIdAndActiveTrue(STORE_ID))
                .thenReturn(List.of(oldSubscription));

        // when
        executor.applyPaidSubscriptionInTx(PAYMENT_ID, paidInfo);

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
        when(aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
        PortOnePaymentInfo failedInfo = portoneInfo("FAILED", new BigDecimal("9900"));

        // when & then
        assertThatThrownBy(() -> executor.applyPaidSubscriptionInTx(PAYMENT_ID, failedInfo))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_COMPLETED);
        verify(aiPlanSubscriptionRepository, never()).save(any());
    }

    @Test
    void 결제_금액이_불일치하면_PAYMENT_AMOUNT_MISMATCH_예외가_발생한다() {
        // given
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        when(aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
        PortOnePaymentInfo tamperedInfo = portoneInfo("PAID", new BigDecimal("100")); // 위변조 금액

        // when & then
        assertThatThrownBy(() -> executor.applyPaidSubscriptionInTx(PAYMENT_ID, tamperedInfo))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        verify(aiPlanSubscriptionRepository, never()).save(any());
    }

    @Test
    void 부분_취소된_결제는_늦은_PAID_재전송으로_구독을_복구하지_않는다() {
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        payment.markPaid(LocalDateTime.now());
        payment.partiallyCancel();
        when(aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        executor.applyPaidSubscriptionInTx(PAYMENT_ID, portoneInfo("PAID", new BigDecimal("9900")));

        assertThat(payment.getStatus()).isEqualTo(AiPlanPaymentStatus.PARTIALLY_CANCELLED);
        verify(aiPlanSubscriptionRepository, never()).save(any(AiPlanSubscription.class));
    }

    @Test
    void 구독을_아직_반영하지_않은_결제의_부분_취소도_종결_상태로_기록한다() {
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        when(aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        executor.partiallyCancelPaidSubscriptionInTx(PAYMENT_ID);

        assertThat(payment.getStatus()).isEqualTo(AiPlanPaymentStatus.PARTIALLY_CANCELLED);
        verify(aiPlanSubscriptionRepository, never()).findByPayment_AiPlanPaymentId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void 성공한_환불_작업은_늦은_실패_응답으로_되돌아가지_않는다() {
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        AiPlanPaymentCancellationOperation operation = AiPlanPaymentCancellationOperation.request(
                payment, new BigDecimal("9900"));
        operation.recordSucceeded("cancel-1", new BigDecimal("9900"));

        operation.recordFailed("cancel-1", null);

        assertThat(operation.getStatus()).isEqualTo(AiPlanPaymentCancellationStatus.SUCCEEDED);
    }

    @Test
    void 부분_취소된_결제도_전액_취소_Webhook에서_CANCELLED로_수렴한다() {
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        payment.markPaid(LocalDateTime.now());
        payment.partiallyCancel();
        when(aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        executor.cancelPaidSubscriptionInTx(PAYMENT_ID);

        assertThat(payment.getStatus()).isEqualTo(AiPlanPaymentStatus.CANCELLED);
    }

    @Test
    void Webhook_중복_수신_시_구독은_한_번만_반영된다() {
        // given — 이미 PAID 처리된 결제 + 이미 매칭되는 활성 구독도 존재 (정상 케이스)
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        payment.markPaid(LocalDateTime.now());
        when(aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
        AiPlanSubscription existingSubscription = AiPlanSubscription.create(store, AiPlanType.BASIC, LocalDateTime.now());
        when(aiPlanSubscriptionRepository.findByPayment_AiPlanPaymentId(1L))
                .thenReturn(Optional.of(existingSubscription));

        // when
        executor.applyPaidSubscriptionInTx(PAYMENT_ID, portoneInfo("PAID", new BigDecimal("9900")));

        // then — 구독 생성은 일어나지 않는다
        verify(aiPlanSubscriptionRepository, never()).save(any());
    }

    @Test
    void 결제는_PAID인데_구독이_누락되면_복구된다() {
        // given — payment.isPaid()=true 이지만 해당 플랜의 활성 구독이 전혀 없는 장애/버그 상황 재현
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        payment.markPaid(LocalDateTime.now());
        when(aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(aiPlanSubscriptionRepository.findByStore_StoreIdAndActiveTrue(STORE_ID))
                .thenReturn(List.of());

        // when
        executor.applyPaidSubscriptionInTx(PAYMENT_ID, portoneInfo("PAID", new BigDecimal("9900")));

        // then — 이미 검증된 결제를 기준으로 누락된 구독을 복구 생성한다
        verify(aiPlanSubscriptionRepository).save(any(AiPlanSubscription.class));
    }

    // ──────────────────── prepareMismatchedPaymentCancellation ────────────────────

    @Test
    void 금액_불일치_환불_작업은_PortOne_호출_전에_PENDING으로_저장한다() {
        // given
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        when(aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(PAYMENT_ID))
                .thenReturn(Optional.of(payment));
        when(cancellationOperationRepository.findByPaymentIdWithPessimisticLock(PAYMENT_ID))
                .thenReturn(Optional.empty());
        when(cancellationOperationRepository.save(any(AiPlanPaymentCancellationOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        AiPlanPaymentCancellationPlan plan = executor.prepareMismatchedPaymentCancellation(
                PAYMENT_ID, new BigDecimal("9900"));

        // then
        assertThat(plan.shouldCallPortOne()).isTrue();
        org.mockito.ArgumentCaptor<AiPlanPaymentCancellationOperation> captor =
                org.mockito.ArgumentCaptor.forClass(AiPlanPaymentCancellationOperation.class);
        verify(cancellationOperationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AiPlanPaymentCancellationStatus.PENDING);
    }

    @Test
    void PortOne_호출_전에_중단된_PENDING_환불은_같은_멱등키로_재시도한다() {
        // given
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        AiPlanPaymentCancellationOperation operation =
                AiPlanPaymentCancellationOperation.request(payment, new BigDecimal("9900"));
        when(aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(PAYMENT_ID))
                .thenReturn(Optional.of(payment));
        when(cancellationOperationRepository.findByPaymentIdWithPessimisticLock(PAYMENT_ID))
                .thenReturn(Optional.of(operation));

        // when
        AiPlanPaymentCancellationPlan plan = executor.prepareMismatchedPaymentCancellation(
                PAYMENT_ID, new BigDecimal("9900"));

        // then
        assertThat(plan.shouldCallPortOne()).isTrue();
        assertThat(plan.idempotencyKey()).isEqualTo(operation.getIdempotencyKey());
        assertThat(operation.getStatus()).isEqualTo(AiPlanPaymentCancellationStatus.PENDING);
    }

    @Test
    void PortOne이_접수한_REQUESTED_환불은_중복_호출하지_않는다() {
        // given
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        AiPlanPaymentCancellationOperation operation =
                AiPlanPaymentCancellationOperation.request(payment, new BigDecimal("9900"));
        operation.recordRequested("cancel-1");
        when(aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(PAYMENT_ID))
                .thenReturn(Optional.of(payment));
        when(cancellationOperationRepository.findByPaymentIdWithPessimisticLock(PAYMENT_ID))
                .thenReturn(Optional.of(operation));

        // when
        AiPlanPaymentCancellationPlan plan = executor.prepareMismatchedPaymentCancellation(
                PAYMENT_ID, new BigDecimal("9900"));

        // then
        assertThat(plan.shouldCallPortOne()).isFalse();
    }

    // ──────────────────── 구독 기간 정책 ────────────────────

    @Test
    void 상위_플랜_결제는_즉시_적용되고_남은_기간만큼_종료일이_밀린다() {
        // given — BASIC이 10일 남은 상태에서 PRO 결제
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.PRO);
        when(aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(PAYMENT_ID))
                .thenReturn(Optional.of(payment));
        LocalDateTime remainingUntil = LocalDateTime.now().plusDays(10);
        AiPlanSubscription basic = AiPlanSubscription.createWithPeriod(
                store, AiPlanType.BASIC, LocalDateTime.now().minusDays(20), remainingUntil);
        when(aiPlanSubscriptionRepository.findByStore_StoreIdAndActiveTrue(STORE_ID))
                .thenReturn(List.of(basic));

        // when
        executor.applyPaidSubscriptionInTx(PAYMENT_ID, portoneInfo("PAID", new BigDecimal("19900")));

        // then
        AiPlanSubscription saved = savedSubscription();
        assertThat(basic.isActive()).isFalse();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getPlanType()).isEqualTo(AiPlanType.PRO);
        assertThat(saved.getExpiredAt()).isEqualTo(remainingUntil.plusMonths(1));
    }

    @Test
    void 하위_플랜_결제는_현재_기간이_끝난_뒤로_예약된다() {
        // given — PRO가 10일 남은 상태에서 BASIC 결제
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        when(aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(PAYMENT_ID))
                .thenReturn(Optional.of(payment));
        LocalDateTime remainingUntil = LocalDateTime.now().plusDays(10);
        AiPlanSubscription pro = AiPlanSubscription.createWithPeriod(
                store, AiPlanType.PRO, LocalDateTime.now().minusDays(20), remainingUntil);
        when(aiPlanSubscriptionRepository.findByStore_StoreIdAndActiveTrue(STORE_ID))
                .thenReturn(List.of(pro));

        // when
        executor.applyPaidSubscriptionInTx(PAYMENT_ID, portoneInfo("PAID", new BigDecimal("9900")));

        // then — 남은 PRO 권한을 깎지 않는다
        AiPlanSubscription saved = savedSubscription();
        assertThat(pro.isActive()).isTrue();
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getStartedAt()).isEqualTo(remainingUntil);
        assertThat(saved.getExpiredAt()).isEqualTo(remainingUntil.plusMonths(1));
    }

    @Test
    void 남은_기간이_없으면_결제_시점부터_한_달을_적용한다() {
        // given
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        when(aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(PAYMENT_ID))
                .thenReturn(Optional.of(payment));
        AiPlanSubscription expired = AiPlanSubscription.createWithPeriod(
                store, AiPlanType.BASIC, LocalDateTime.now().minusMonths(2), LocalDateTime.now().minusDays(1));
        when(aiPlanSubscriptionRepository.findByStore_StoreIdAndActiveTrue(STORE_ID))
                .thenReturn(List.of(expired));

        // when
        executor.applyPaidSubscriptionInTx(PAYMENT_ID, portoneInfo("PAID", new BigDecimal("9900")));

        // then
        AiPlanSubscription saved = savedSubscription();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getExpiredAt()).isEqualTo(saved.getStartedAt().plusMonths(1));
    }

    @Test
    void 만료로_실패_처리된_결제는_뒤늦게_승인돼도_구독을_만들지_않는다() {
        // given — 결제 대기 만료로 FAILED가 된 뒤 PAID Webhook이 도착
        Store store = stubStore();
        AiPlanPayment payment = pendingPayment(store, AiPlanType.BASIC);
        payment.markFailed();
        when(aiPlanPaymentRepository.findByPortonePaymentIdWithPessimisticLock(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        // when — 종결된 결제의 PAID 재전송은 무시된다. 환불은 호출부(서비스)가 맡는다
        executor.applyPaidSubscriptionInTx(PAYMENT_ID, portoneInfo("PAID", new BigDecimal("9900")));

        // then
        assertThat(payment.getStatus()).isEqualTo(AiPlanPaymentStatus.FAILED);
        verify(aiPlanSubscriptionRepository, never()).save(any(AiPlanSubscription.class));
    }

    private AiPlanSubscription savedSubscription() {
        org.mockito.ArgumentCaptor<AiPlanSubscription> captor =
                org.mockito.ArgumentCaptor.forClass(AiPlanSubscription.class);
        verify(aiPlanSubscriptionRepository).save(captor.capture());
        return captor.getValue();
    }
}
