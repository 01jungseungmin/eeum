package com.eeum.eeum.domain.order.entity;

import com.eeum.eeum.domain.order.enums.PaymentCancellationStatus;
import com.eeum.eeum.domain.order.enums.PaymentCancellationTrigger;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentCancellationOperationTest {

    @Test
    void PG_취소가_확정된_수동검토_작업만_내부반영_재시도를_허용한다() {
        // given
        Order order = mock(Order.class);
        Payment payment = mock(Payment.class);
        when(payment.getOrder()).thenReturn(order);
        when(payment.getAmount()).thenReturn(BigDecimal.valueOf(10_000));
        PaymentCancellationOperation operation = PaymentCancellationOperation.start(
                order, payment, PaymentCancellationTrigger.CUSTOMER_CANCEL,
                "고객 취소", BigDecimal.valueOf(10_000));
        operation.markPgRequested(PaymentCancellationTrigger.CUSTOMER_CANCEL, "고객 취소", LocalDateTime.now());
        operation.markPgCancelled("cancel-1", "SUCCEEDED", BigDecimal.valueOf(10_000), LocalDateTime.now());
        operation.requireManualReview("INTERNAL_APPLY_FAILED", "내부 반영 실패");

        // when
        operation.resumeConfirmedPgCancellation();

        // then
        assertThat(operation.isPgCancelled()).isTrue();
    }

    @Test
    void PG_응답_유실_작업은_유예_시간_후에만_수동검토로_격리된다() {
        // given
        Order order = mock(Order.class);
        Payment payment = mock(Payment.class);
        when(payment.getOrder()).thenReturn(order);
        when(payment.getAmount()).thenReturn(BigDecimal.valueOf(10_000));
        PaymentCancellationOperation operation = PaymentCancellationOperation.start(
                order, payment, PaymentCancellationTrigger.CUSTOMER_CANCEL,
                "고객 취소", BigDecimal.valueOf(10_000));
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 11, 10, 0);
        operation.markPgRequested(PaymentCancellationTrigger.CUSTOMER_CANCEL, "고객 취소", requestedAt);

        // when & then - 선행 요청이 아직 응답을 반영할 수 있는 시간에는 상태를 보존한다.
        assertThat(operation.requireManualReviewForUnknownPg(
                Duration.ofMinutes(2), requestedAt.plusMinutes(1), "PG_CANCEL_OUTCOME_UNKNOWN", "응답 유실"))
                .isFalse();
        assertThat(operation.getStatus()).isEqualTo(PaymentCancellationStatus.PG_CANCEL_REQUESTED);

        // when & then - 유예가 지나면 운영 수습 대기열로 남긴다.
        assertThat(operation.requireManualReviewForUnknownPg(
                Duration.ofMinutes(2), requestedAt.plusMinutes(2), "PG_CANCEL_OUTCOME_UNKNOWN", "응답 유실"))
                .isTrue();
        assertThat(operation.getStatus()).isEqualTo(PaymentCancellationStatus.MANUAL_REVIEW_REQUIRED);
        assertThat(operation.getFailureCode()).isEqualTo("PG_CANCEL_OUTCOME_UNKNOWN");
    }

    @Test
    void 부분_취소는_SUCCEEDED여도_전액_취소_재처리를_허용하지_않는다() {
        // given — PG 상태만 SUCCEEDED이고 취소 금액은 전액보다 작다.
        Order order = mock(Order.class);
        Payment payment = mock(Payment.class);
        when(payment.getOrder()).thenReturn(order);
        when(payment.getAmount()).thenReturn(BigDecimal.valueOf(10_000));
        PaymentCancellationOperation operation = PaymentCancellationOperation.start(
                order, payment, PaymentCancellationTrigger.CUSTOMER_CANCEL,
                "고객 취소", BigDecimal.valueOf(10_000));
        operation.markPgRequested(PaymentCancellationTrigger.CUSTOMER_CANCEL, "고객 취소", LocalDateTime.now());
        operation.recordPgStatus("cancel-1", "SUCCEEDED", BigDecimal.valueOf(3_000));
        operation.requireManualReview("PG_CANCEL_AMOUNT_MISMATCH", "부분 취소");

        // when / then
        assertThatThrownBy(operation::resumeConfirmedPgCancellation)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_CANCELLATION_INVALID_STATUS);
    }
}
