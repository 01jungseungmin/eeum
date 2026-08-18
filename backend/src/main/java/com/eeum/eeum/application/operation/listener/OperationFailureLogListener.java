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
 * <p>{@code AFTER_COMPLETION} — 커밋뿐 아니라 <b>롤백된 경우에도</b> 실행된다.
 * 실패 이력은 원 작업이 실패했을 때가 정확히 필요한 시점이므로 {@code AFTER_COMMIT}이면 안 된다.
 *
 * <p>{@code fallbackExecution = true} — 트랜잭션 밖에서 발행된 이벤트도 처리한다.
 * 스케줄러 ErrorHandler나 Webhook 파싱 실패는 트랜잭션이 열리기 전에 발생한다.
 *
 * <p>{@code @Async} — 별도 스레드에서 실행한다. 원 요청 스레드가 이력 기록을 기다리지 않는다.
 *
 * <p>저장은 {@link OperationFailureLogWriter}에 위임한다. 이 메서드에 직접
 * {@code @Transactional}을 걸면 트랜잭션 경계가 try 블록 <b>바깥</b>에 놓여서,
 * 저장 실패로 rollback-only가 된 트랜잭션의 커밋 예외를 여기서 잡을 수 없다.
 *
 * <p><b>커넥션 점유에 대해</b> — 보통은 이 스레드에 바인딩된 트랜잭션이 없으므로
 * writer의 {@code REQUIRES_NEW}가 커넥션을 1개만 쓴다. 다만
 * {@code AsyncConfig}의 풀(core 4 / max 16 / queue 500)이 포화되면 {@code CallerRunsPolicy}로
 * 호출 스레드에서 인라인 실행되고, 이때는 {@code afterCompletion} 시점이라 원 커넥션이 아직
 * 반납되기 전이라 잠시 2개를 점유한다. 하필 장애로 실패가 몰릴 때 겹치는 구간이므로,
 * 풀 설정을 바꿀 때 이 경로를 함께 고려해야 한다.
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
