package com.eeum.eeum.application.order.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 준비 단계가 확정한, 외부 PG 호출에 필요한 값.
 *
 * 엔티티를 트랜잭션 밖으로 들고 나가면 지연 로딩과 detached 상태를 신경 써야 하므로
 * 필요한 값만 복사해 넘긴다.
 *
 * @param alreadyPgCancelled 이전 시도에서 PG 취소까지는 끝난 작업인지.
 *                           true면 외부를 다시 호출하지 않고 내부 반영만 재시도한다.
 */
public record PaymentCancellationPlan(
        Long operationId,
        String portonePaymentId,
        BigDecimal amount,
        String reason,
        boolean alreadyPgCancelled,
        boolean pgOutcomeUnknown,
        LocalDateTime pgRequestedAt
) {
}
