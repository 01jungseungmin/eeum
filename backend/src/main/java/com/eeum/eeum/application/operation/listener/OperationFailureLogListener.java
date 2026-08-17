package com.eeum.eeum.application.operation.listener;

import com.eeum.eeum.domain.operation.entity.OperationFailureLog;
import com.eeum.eeum.domain.operation.event.OperationFailedEvent;
import com.eeum.eeum.domain.operation.repository.OperationFailureLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 운영 실패 이력을 DB에 남긴다.
 *
 * <p>{@code AFTER_COMPLETION} — 커밋뿐 아니라 <b>롤백된 경우에도</b> 실행된다.
 * 실패 이력은 원 작업이 실패했을 때가 정확히 필요한 시점이므로 {@code AFTER_COMMIT}이면 안 된다.
 *
 * <p>{@code fallbackExecution = true} — 트랜잭션 밖에서 발행된 이벤트도 처리한다.
 * 스케줄러 ErrorHandler나 Webhook 파싱 실패는 트랜잭션이 열리기 전에 발생한다.
 *
 * <p>{@code @Async} — 별도 스레드에서 자체 트랜잭션을 연다.
 * 원 트랜잭션 안에서 {@code REQUIRES_NEW}로 기록하면 한 요청이 DB 커넥션을 2개 점유해
 * 기본 풀 크기(10)에서 동시 5건이 상한이 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationFailureLogListener {

    private final OperationFailureLogRepository operationFailureLogRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION, fallbackExecution = true)
    @Transactional
    public void onOperationFailed(OperationFailedEvent event) {
        try {
            operationFailureLogRepository.save(OperationFailureLog.create(
                    event.category(),
                    event.operation(),
                    event.refType(),
                    event.refId(),
                    event.errorCode(),
                    event.errorMessage(),
                    event.payload()
            ));
        } catch (RuntimeException e) {
            // 이력 기록 실패가 다른 흐름에 영향을 주면 안 된다. 최소한 로그로는 남긴다.
            log.error("운영 실패 이력 저장 실패 — category={}, operation={}, errorCode={}",
                    event.category(), event.operation(), event.errorCode(), e);
        }
    }
}
