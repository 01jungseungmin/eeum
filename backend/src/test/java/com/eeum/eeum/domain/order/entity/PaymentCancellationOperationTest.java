package com.eeum.eeum.domain.order.entity;

import com.eeum.eeum.domain.order.enums.PaymentCancellationStatus;
import com.eeum.eeum.domain.order.enums.PaymentCancellationTrigger;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentCancellationOperationTest {

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
}
