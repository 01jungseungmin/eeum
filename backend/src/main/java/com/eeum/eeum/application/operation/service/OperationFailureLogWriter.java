package com.eeum.eeum.application.operation.service;

import com.eeum.eeum.domain.operation.entity.OperationFailureLog;
import com.eeum.eeum.domain.operation.event.OperationFailedEvent;
import com.eeum.eeum.domain.operation.repository.OperationFailureLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실패 이력을 독립 트랜잭션으로 저장한다.
 *
 * <p>{@code REQUIRES_NEW}인 이유: 실패 이력은 원 작업이 롤백돼도 남아야 하는 운영 데이터다.
 * 원 트랜잭션에 참여하면 "환불 실패로 롤백" 같은 상황에서 이력까지 함께 사라진다.
 *
 * <p><b>별도 빈으로 분리한 이유</b>는 전파 속성 때문이다. 호출자와 같은 클래스에 두고
 * {@code this.write(...)}로 부르면 프록시를 거치지 않아 {@code REQUIRES_NEW}가 조용히 무시된다.
 *
 * <p>또 하나, 트랜잭션 경계가 호출자의 try 블록 <b>안쪽</b>에 놓인다는 점이 중요하다.
 * 커밋 실패({@code UnexpectedRollbackException} 등)까지 호출자가 잡을 수 있어야
 * 이력 기록 실패가 다른 흐름으로 번지지 않는다.
 */
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
