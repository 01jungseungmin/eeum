package com.eeum.eeum.application.operation.listener;

import com.eeum.eeum.domain.operation.entity.OperationFailureLog;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.domain.operation.event.OperationFailedEvent;
import com.eeum.eeum.domain.operation.event.OperationFailureRecordedEvent;
import com.eeum.eeum.domain.operation.repository.OperationFailureLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationFailureLogListenerTest {

    private static final Long LOG_ID = 42L;

    @Mock private OperationFailureLogRepository operationFailureLogRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OperationFailureLogListener listener;

    @Test
    void 이력을_저장한_뒤_저장된_ID로_기록완료_이벤트를_발행한다() {
        // given — 알림이 대시보드의 특정 건을 가리키려면 저장 후 부여된 ID가 필요하다
        when(operationFailureLogRepository.save(any(OperationFailureLog.class)))
                .thenAnswer(invocation -> {
                    OperationFailureLog log = invocation.getArgument(0);
                    ReflectionTestUtils.setField(log, "operationFailureLogId", LOG_ID);
                    return log;
                });

        // when
        listener.onOperationFailed(new OperationFailedEvent(
                OperationFailureCategory.REFUND, "PaymentService.cancelPayment",
                "PAYMENT", "1", "PORTONE_TIMEOUT", "타임아웃", "payload"));

        // then
        ArgumentCaptor<OperationFailureRecordedEvent> captor =
                ArgumentCaptor.forClass(OperationFailureRecordedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        OperationFailureRecordedEvent published = captor.getValue();
        assertThat(published.operationFailureLogId()).isEqualTo(LOG_ID);
        assertThat(published.category()).isEqualTo(OperationFailureCategory.REFUND);
        assertThat(published.operation()).isEqualTo("PaymentService.cancelPayment");
        assertThat(published.errorCode()).isEqualTo("PORTONE_TIMEOUT");
    }

    @Test
    void 이력_저장이_실패하면_알림_이벤트를_발행하지_않고_예외도_전파하지_않는다() {
        // given — 대시보드에 없는 건을 가리키는 알림이 나가면 안 되고,
        // 이력 기록 실패가 원 작업 흐름을 깨서도 안 된다
        when(operationFailureLogRepository.save(any(OperationFailureLog.class)))
                .thenThrow(new RuntimeException("DB 연결 실패"));

        // when & then
        assertThatCode(() -> listener.onOperationFailed(new OperationFailedEvent(
                OperationFailureCategory.SCHEDULER, "OrderExpirationScheduler.expire",
                "SCHEDULER", null, "NPE", "널", null)))
                .doesNotThrowAnyException();

        verify(eventPublisher, never()).publishEvent(any(OperationFailureRecordedEvent.class));
    }
}
