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

/**
 * 운영 실패 이력을 남긴다.
 *
 * AFTER_COMPLETION이라 롤백된 경우에도 실행된다 — 실패 이력이 필요한 시점이 바로 그때다.
 * fallbackExecution은 트랜잭션이 열리기 전에 터지는 스케줄러 ErrorHandler·Webhook
 * 파싱 실패를 받기 위한 것이다. 저장을 writer에 위임하는 이유는, 여기에 직접 Transactional을
 * 걸면 경계가 try 바깥에 놓여 rollback-only 트랜잭션의 커밋 예외를 잡지 못하기 때문이다.
 */
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
