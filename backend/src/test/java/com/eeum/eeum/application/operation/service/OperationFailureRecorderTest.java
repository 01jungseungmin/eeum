package com.eeum.eeum.application.operation.service;

import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.domain.operation.event.OperationFailedEvent;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OperationFailureRecorderTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OperationFailureRecorder operationFailureRecorder;

    @Test
    void 도메인_예외는_ErrorCode_이름을_에러코드로_기록한다() {
        // given
        BusinessException error = new BusinessException(ErrorCode.PAYMENT_REFUND_FAILED);

        // when
        operationFailureRecorder.record(
                OperationFailureCategory.REFUND,
                "PaymentService.cancelPayment",
                "PAYMENT", "42",
                error,
                "amount=10000");

        // then
        ArgumentCaptor<OperationFailedEvent> captor =
                ArgumentCaptor.forClass(OperationFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        OperationFailedEvent event = captor.getValue();
        assertThat(event.category()).isEqualTo(OperationFailureCategory.REFUND);
        assertThat(event.operation()).isEqualTo("PaymentService.cancelPayment");
        assertThat(event.refType()).isEqualTo("PAYMENT");
        assertThat(event.refId()).isEqualTo("42");
        assertThat(event.errorCode()).isEqualTo(ErrorCode.PAYMENT_REFUND_FAILED.name());
        assertThat(event.payload()).isEqualTo("amount=10000");
    }

    @Test
    void 도메인_예외가_아니면_예외_클래스명을_에러코드로_기록한다() {
        // given
        IllegalStateException error = new IllegalStateException("커넥션 없음");

        // when
        operationFailureRecorder.record(
                OperationFailureCategory.SCHEDULER,
                "OrderExpirationScheduler.expireOrders",
                "SCHEDULER", null,
                error,
                null);

        // then
        ArgumentCaptor<OperationFailedEvent> captor =
                ArgumentCaptor.forClass(OperationFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().errorCode()).isEqualTo("IllegalStateException");
        assertThat(captor.getValue().errorMessage()).isEqualTo("커넥션 없음");
    }

    @Test
    void 이력_발행이_실패해도_원_작업으로_예외를_전파하지_않는다() {
        // given: 이벤트 발행 자체가 터지는 상황
        doThrow(new IllegalStateException("publisher down"))
                .when(eventPublisher).publishEvent(any(OperationFailedEvent.class));

        // when & then: 실패 기록이 결제 처리를 막아서는 안 된다
        assertThatCode(() -> operationFailureRecorder.record(
                OperationFailureCategory.PAYMENT_WEBHOOK,
                "PaymentService.handleWebhook",
                null, null,
                new IllegalStateException("원래 실패"),
                null))
                .doesNotThrowAnyException();
    }
}
