package com.eeum.eeum.application.operation.listener;

import com.eeum.eeum.application.operation.service.OperationFailureLogWriter;
import com.eeum.eeum.domain.operation.entity.OperationFailureLog;
import com.eeum.eeum.domain.operation.event.OperationFailedEvent;
import com.eeum.eeum.domain.operation.event.OperationFailureRecordedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 실패 이력을 AFTER_COMPLETION에서 별도 저장한다. 트랜잭션 밖 이벤트와 롤백도 기록해야 한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationFailureLogListener {

    private final OperationFailureLogWriter operationFailureLogWriter;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION, fallbackExecution = true)
    public void onOperationFailed(OperationFailedEvent event) {
        OperationFailureLog saved;
        try {
            saved = operationFailureLogWriter.write(event);
        } catch (RuntimeException e) {
            // 이력 기록 실패가 다른 흐름에 영향을 주면 안 된다. 최소한 로그로는 남긴다.
            log.error("운영 실패 이력 저장 실패 — category={}, operation={}, errorCode={}",
                    event.category(), event.operation(), event.errorCode(), e);
            return;
        }

        try {
            // 저장 트랜잭션이 이미 커밋된 뒤라 알림이 가리키는 행이 실제로 존재한다.
            eventPublisher.publishEvent(new OperationFailureRecordedEvent(
                    saved.getOperationFailureLogId(),
                    saved.getCategory(),
                    saved.getOperation(),
                    saved.getErrorCode()
            ));
        } catch (RuntimeException e) {
            // 알림 발행이 실패해도 이력은 이미 남았다. 되돌릴 것이 없다.
            log.error("운영 실패 알림 발행 실패 — logId={}", saved.getOperationFailureLogId(), e);
        }
    }
}
