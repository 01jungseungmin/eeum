package com.eeum.eeum.domain.operation.event;

import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;

/**
 * 운영 실패 발생 이벤트.
 *
 * <p>실패를 감지한 쪽은 이 이벤트만 발행하고 DB 기록은 리스너가 담당한다.
 * 기록을 같은 트랜잭션에서 하면 (1) 원 작업이 롤백될 때 이력까지 사라지고,
 * (2) {@code REQUIRES_NEW}로 피하려 하면 한 요청이 DB 커넥션을 2개 점유한다.
 */
public record OperationFailedEvent(
        OperationFailureCategory category,
        String operation,
        String refType,
        String refId,
        String errorCode,
        String errorMessage,
        String payload
) {}
