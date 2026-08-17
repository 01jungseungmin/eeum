package com.eeum.eeum.application.operation.listener;

import com.eeum.eeum.domain.operation.entity.OperationFailureLog;
import com.eeum.eeum.domain.operation.event.OperationFailedEvent;
import com.eeum.eeum.domain.operation.repository.OperationFailureLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
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
 * <p>{@code @Async} — 별도 스레드에서 실행한다. 원 요청 스레드가 이력 기록을 기다리지 않는다.
 *
 * <p>{@code REQUIRES_NEW} — Spring은 {@code @TransactionalEventListener}에 REQUIRED 트랜잭션을
 * 허용하지 않는다(이미 완료 중인 트랜잭션에 다시 참여하게 되므로). REQUIRES_NEW 또는 NOT_SUPPORTED만 가능하다.
 *
 * <p>여기서 REQUIRES_NEW는 커넥션을 2개 점유하지 않는다 — {@code @Async}로 <b>별도 스레드</b>에서
 * 실행되고 그 스레드에는 바인딩된 트랜잭션이 없기 때문이다. 커넥션 2배 점유 문제는
 * 원 트랜잭션이 살아있는 같은 스레드에서 REQUIRES_NEW를 호출할 때 생긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationFailureLogListener {

    private final OperationFailureLogRepository operationFailureLogRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
