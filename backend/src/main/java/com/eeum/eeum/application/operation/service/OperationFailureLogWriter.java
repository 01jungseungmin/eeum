package com.eeum.eeum.application.operation.service;

import com.eeum.eeum.domain.operation.entity.OperationFailureLog;
import com.eeum.eeum.domain.operation.event.OperationFailedEvent;
import com.eeum.eeum.domain.operation.repository.OperationFailureLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 실패 이력을 독립 트랜잭션으로 저장한다. */
@Component
@RequiredArgsConstructor
public class OperationFailureLogWriter {

    private final OperationFailureLogRepository operationFailureLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OperationFailureLog write(OperationFailedEvent event) {
        return operationFailureLogRepository.save(OperationFailureLog.create(
                event.category(),
                event.operation(),
                event.refType(),
                event.refId(),
                event.errorCode(),
                event.errorMessage(),
                event.payload()
        ));
    }
}
