package com.eeum.eeum.application.order.service;

import com.eeum.eeum.application.operation.service.OperationFailureRecorder;
import com.eeum.eeum.application.order.dto.response.PaymentCancellationPlan;
import com.eeum.eeum.application.order.dto.response.PortOneCancelResult;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.domain.order.enums.PaymentCancellationTrigger;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.PortOnePaymentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 전액 취소 오케스트레이션의 단계 전이 규칙을 고정한다.
 *
 * <p>중점은 <b>PG 취소가 성공한 뒤의 실패 처리</b>다. 외부 취소는 롤백되지 않으므로,
 * 내부 반영이 실패했을 때 조용히 예외로 끝내면 고객 돈만 돌아가고 사장에게는 그대로
 * 지급된다. 재시도 → 수동 검토 격리 → 이력 기록이 순서대로 일어나야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentCancellationServiceTest {

    private static final Long ORDER_ID = 10L;
    private static final Long OPERATION_ID = 99L;
    private static final BigDecimal AMOUNT = new BigDecimal("10000");

    @Mock private RedisLockService redisLockService;
    @Mock private PortOnePaymentClient portOnePaymentClient;
    @Mock private PaymentCancellationProcessor processor;
    @Mock private OperationFailureRecorder operationFailureRecorder;

    private PaymentCancellationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentCancellationService(
                redisLockService, portOnePaymentClient, processor, operationFailureRecorder);

        // 락 자체의 의미는 PaymentWebhookCancellationLockTest가 검증한다.
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(redisLockService).executeWithLock(anyString(), any(Duration.class), any(ErrorCode.class),
                        any(Supplier.class));

        when(processor.prepare(eq(ORDER_ID), any(), anyString())).thenReturn(plan(false, false));
    }

    // ─────────────────── 정상 경로 ───────────────────

    @Test
    void 전액_취소가_성공하면_PG_확정_후_내부에_반영한다() {
        // given
        when(portOnePaymentClient.cancelPayment(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PortOneCancelResult("SUCCEEDED", "c-1", AMOUNT));

        // when
        service.cancel(ORDER_ID, PaymentCancellationTrigger.CUSTOMER_CANCEL, "고객 요청 취소");

        // then — 확정 사실을 먼저 커밋하고 그 뒤에 내부를 반영한다
        verify(processor).markPgCancelled(eq(OPERATION_ID), any());
        verify(processor).applyCancellation(
                eq(OPERATION_ID), eq(PaymentCancellationTrigger.CUSTOMER_CANCEL), anyString());
        verify(processor, never()).requireManualReview(any(), anyString(), anyString());
    }

    @Test
    void 이미_완료된_취소는_PG를_다시_호출하지_않는다() {
        // given — 네 진입점이 같은 주문에 겹쳐 들어와도 한 번만 돈다
        when(processor.prepare(eq(ORDER_ID), any(), anyString())).thenReturn(null);

        // when
        service.cancel(ORDER_ID, PaymentCancellationTrigger.CUSTOMER_CANCEL, "재요청");

        // then
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), any(), anyString(), anyString());
        verify(processor, never()).applyCancellation(any(), any(), anyString());
    }

    @Test
    void 외부에서_이미_취소된_건은_PG를_호출하지_않고_내부만_맞춘다() {
        // given — PortOne 취소 Webhook. PG는 이미 취소됐다.

        // when
        service.cancel(ORDER_ID, PaymentCancellationTrigger.PORTONE_WEBHOOK, "외부 취소", true);

        // then
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), any(), anyString(), anyString());
        verify(processor).markPgCancelled(eq(OPERATION_ID), any());
        verify(processor).applyCancellation(eq(OPERATION_ID), eq(PaymentCancellationTrigger.PORTONE_WEBHOOK), anyString());
    }

    // ─────────────────── PG 미확정 ───────────────────

    @Test
    void REQUESTED_응답은_취소_완료로_확정하지_않고_격리한다() {
        // given — 승인 대기 상태를 완료로 확정하면 미완료 취소가 완료로 기록된다
        when(portOnePaymentClient.cancelPayment(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PortOneCancelResult("REQUESTED", "c-2", AMOUNT));

        // when & then
        assertThatThrownBy(() -> service.cancel(ORDER_ID, PaymentCancellationTrigger.CUSTOMER_CANCEL, "사유"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_CANCELLATION_MANUAL_REVIEW);

        verify(processor).recordPendingPgStatus(eq(OPERATION_ID), any());
        verify(processor, never()).applyCancellation(any(), any(), anyString());
    }

    @Test
    void 부분만_취소된_응답은_내부에_반영하지_않고_격리한다() {
        // given — 전액 취소를 요청했는데 3000원만 취소됐다
        when(portOnePaymentClient.cancelPayment(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PortOneCancelResult("SUCCEEDED", "c-3", new BigDecimal("3000")));

        // when & then
        assertThatThrownBy(() -> service.cancel(ORDER_ID, PaymentCancellationTrigger.CUSTOMER_CANCEL, "사유"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_CANCELLATION_MANUAL_REVIEW);

        verify(operationFailureRecorder).record(
                eq(OperationFailureCategory.REFUND), anyString(), eq("order"), eq(String.valueOf(ORDER_ID)),
                eq("PG_CANCEL_AMOUNT_MISMATCH"), anyString(), anyString());
        verify(processor, never()).applyCancellation(any(), any(), anyString());
    }

    @Test
    void 취소_금액을_읽지_못해도_취소를_막지는_않고_이력만_남긴다() {
        // given — 응답 형태가 바뀌어 금액이 비었다. 전액 취소만 요청하므로 취소 자체는 성립한다.
        when(portOnePaymentClient.cancelPayment(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PortOneCancelResult("SUCCEEDED", "c-4", null));

        // when
        assertThatCode(() -> service.cancel(ORDER_ID, PaymentCancellationTrigger.CUSTOMER_CANCEL, "사유"))
                .doesNotThrowAnyException();

        // then — 대사할 수 있게 기록은 남기되 정상 처리한다
        verify(operationFailureRecorder).record(
                eq(OperationFailureCategory.REFUND), anyString(), eq("order"), eq(String.valueOf(ORDER_ID)),
                eq("PG_CANCEL_AMOUNT_UNVERIFIED"), anyString(), anyString());
        verify(processor).applyCancellation(any(), any(), anyString());
    }

    @Test
    void PG_호출이_실패하면_재시도_가능_상태로_되돌린다() {
        // given — 돈이 움직이지 않았으므로 수동 검토가 아니라 재시도 대상이다
        PortOnePaymentException failure =
                new PortOnePaymentException(ErrorCode.PAYMENT_REFUND_FAILED, "timeout");
        when(portOnePaymentClient.cancelPayment(anyString(), any(), anyString(), anyString()))
                .thenThrow(failure);

        // when & then
        assertThatThrownBy(() -> service.cancel(ORDER_ID, PaymentCancellationTrigger.CUSTOMER_CANCEL, "사유"))
                .isInstanceOf(PortOnePaymentException.class);

        verify(processor).markPgFailed(eq(OPERATION_ID), anyString(), anyString());
        verify(processor, never()).requireManualReview(any(), anyString(), anyString());
        verify(processor, never()).applyCancellation(any(), any(), anyString());
    }

    // ─────────────────── PG 성공 후 내부 반영 실패 ───────────────────

    @Test
    void 마감과_경합하면_재시도하고_성공하면_수동검토로_넘기지_않는다() {
        // given — 첫 시도는 마감 스케줄러와 부딪혀 실패, 두 번째는 성공
        when(portOnePaymentClient.cancelPayment(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PortOneCancelResult("SUCCEEDED", "c-5", AMOUNT));
        doThrow(new BusinessException(ErrorCode.SETTLEMENT_CONCURRENT_MODIFICATION))
                .doNothing()
                .when(processor).applyCancellation(any(), any(), anyString());

        // when
        assertThatCode(() -> service.cancel(ORDER_ID, PaymentCancellationTrigger.CUSTOMER_CANCEL, "사유"))
                .doesNotThrowAnyException();

        // then
        verify(processor, times(2)).applyCancellation(any(), any(), anyString());
        verify(processor, never()).requireManualReview(any(), anyString(), anyString());
    }

    @Test
    void 경합이_계속되면_수동검토로_격리하고_이력을_남긴다() {
        // given — PG는 이미 취소됐다. 조용히 예외로 끝내면 고객 돈만 돌아가고 아무도 모른다.
        when(portOnePaymentClient.cancelPayment(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PortOneCancelResult("SUCCEEDED", "c-6", AMOUNT));
        doThrow(new BusinessException(ErrorCode.SETTLEMENT_CONCURRENT_MODIFICATION))
                .when(processor).applyCancellation(any(), any(), anyString());

        // when & then
        assertThatThrownBy(() -> service.cancel(ORDER_ID, PaymentCancellationTrigger.CUSTOMER_CANCEL, "사유"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_CANCELLATION_MANUAL_REVIEW);

        verify(processor, times(3)).applyCancellation(any(), any(), anyString());
        verify(processor).requireManualReview(eq(OPERATION_ID), anyString(), anyString());
        verify(operationFailureRecorder).record(
                eq(OperationFailureCategory.REFUND), eq("PaymentCancellationService.applyCancellation"),
                eq("order"), eq(String.valueOf(ORDER_ID)), any(Throwable.class), anyString());
    }

    @Test
    void 경합이_아닌_실패는_재시도하지_않고_바로_격리한다() {
        // given — 상태 위반은 다시 읽어도 풀리지 않는다. 재시도는 시간만 끈다.
        when(portOnePaymentClient.cancelPayment(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PortOneCancelResult("SUCCEEDED", "c-7", AMOUNT));
        doThrow(new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS))
                .when(processor).applyCancellation(any(), any(), anyString());

        // when & then
        assertThatThrownBy(() -> service.cancel(ORDER_ID, PaymentCancellationTrigger.CUSTOMER_CANCEL, "사유"))
                .isInstanceOf(BusinessException.class);

        verify(processor, times(1)).applyCancellation(any(), any(), anyString());
        verify(processor).requireManualReview(eq(OPERATION_ID), anyString(), anyString());
    }

    // ─────────────────── PG 응답 유실 ───────────────────

    @Test
    void 응답_유실_유예_구간에는_진행중으로_알리고_격리하지_않는다() {
        // given — 선행 호출이 결과를 반영할 수 있는 구간이다. 아직 격리된 것이 없다.
        when(processor.prepare(eq(ORDER_ID), any(), anyString())).thenReturn(plan(false, true));
        when(processor.requireManualReviewForStalePgRequest(eq(OPERATION_ID), any(Duration.class)))
                .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> service.cancel(ORDER_ID, PaymentCancellationTrigger.CUSTOMER_CANCEL, "사유"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_CANCELLATION_IN_PROGRESS);

        verify(portOnePaymentClient, never()).cancelPayment(anyString(), any(), anyString(), anyString());
    }

    @Test
    void 유예_시간이_지난_응답_유실은_수동검토로_격리한다() {
        // given
        when(processor.prepare(eq(ORDER_ID), any(), anyString())).thenReturn(plan(false, true));
        when(processor.requireManualReviewForStalePgRequest(eq(OPERATION_ID), any(Duration.class)))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> service.cancel(ORDER_ID, PaymentCancellationTrigger.CUSTOMER_CANCEL, "사유"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_CANCELLATION_MANUAL_REVIEW);

        verify(operationFailureRecorder).record(
                eq(OperationFailureCategory.REFUND), anyString(), eq("order"), eq(String.valueOf(ORDER_ID)),
                eq("PG_CANCEL_OUTCOME_UNKNOWN"), anyString(), anyString());
    }

    @Test
    void 응답이_유실된_취소도_확정_Webhook이_오면_내부에_반영한다() {
        // given — 최초 취소 요청의 응답은 없었지만 PG가 보낸 CANCELLED Webhook은 확정 신호다.
        when(processor.prepare(eq(ORDER_ID), any(), anyString())).thenReturn(plan(false, true));

        // when
        service.cancel(ORDER_ID, PaymentCancellationTrigger.PORTONE_WEBHOOK, "외부 취소", true);

        // then
        verify(processor).markPgCancelled(eq(OPERATION_ID), any());
        verify(processor).applyCancellation(
                eq(OPERATION_ID), eq(PaymentCancellationTrigger.PORTONE_WEBHOOK), anyString());
        verify(processor, never()).requireManualReviewForStalePgRequest(any(), any());
    }

    private PaymentCancellationPlan plan(boolean alreadyPgCancelled, boolean pgOutcomeUnknown) {
        return new PaymentCancellationPlan(
                OPERATION_ID, "portone-1", AMOUNT, "사유",
                alreadyPgCancelled, pgOutcomeUnknown, LocalDateTime.now());
    }
}
