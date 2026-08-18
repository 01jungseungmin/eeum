package com.eeum.eeum.application.operation.service;

import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import com.eeum.eeum.domain.operation.event.OperationFailedEvent;
import com.eeum.eeum.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 운영 실패를 이력으로 남기는 단일 진입점.
 *
 * <p>catch 블록에서 이 컴포넌트를 호출한다. 실제 DB 기록은
 * {@code OperationFailureLogListener}가 커밋/롤백 완료 후 별도 스레드에서 수행한다.
 *
 * <p><b>기록 자체가 원 작업을 실패시키지 않는다.</b> 이벤트 발행에서 예외가 나도
 * 삼키고 로그만 남긴다 — 이력 기록 실패가 결제 처리를 막으면 주객이 전도된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationFailureRecorder {

    private final ApplicationEventPublisher eventPublisher;

    public void record(
            OperationFailureCategory category,
            String operation,
            String refType,
            String refId,
            Throwable error,
            String payload
    ) {
        record(category, operation, refType, refId, resolveErrorCode(error),
                error != null ? error.getMessage() : null, payload);
    }

    public void record(
            OperationFailureCategory category,
            String operation,
            String refType,
            String refId,
            String errorCode,
            String errorMessage,
            String payload
    ) {
        try {
            eventPublisher.publishEvent(new OperationFailedEvent(
                    category, operation, refType, refId, errorCode, errorMessage, payload));
        } catch (RuntimeException e) {
            log.error("운영 실패 이력 발행 실패 — category={}, operation={}", category, operation, e);
        }
    }

    // 도메인 예외면 ErrorCode를, 그 외에는 예외 클래스명을 코드로 쓴다.
    private String resolveErrorCode(Throwable error) {
        if (error == null) {
            return null;
        }
        if (error instanceof BusinessException businessException) {
            return businessException.getErrorCode().name();
        }
        return error.getClass().getSimpleName();
    }
}
