package com.eeum.eeum.domain.operation.event;

import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;

/**
 * 운영 실패 이력이 DB에 기록된 뒤 발행되는 이벤트.
 *
 * <p>{@link OperationFailedEvent}와 굳이 나눈 이유가 있다. 기록 전에는 이력 ID가 없어
 * 관리자 알림이 대시보드의 해당 건을 가리킬 수 없고, 기록 자체가 실패한 건에까지 알림이 나간다.
 * 알림은 "대시보드에 가서 보라"는 안내이므로 대시보드에 실제로 존재하는 건에 대해서만 발행한다.
 */
public record OperationFailureRecordedEvent(
        Long operationFailureLogId,
        OperationFailureCategory category,
        String operation,
        String errorCode
) {}
