package com.eeum.eeum.application.order.dto.response;

import java.math.BigDecimal;

/** PortOne 취소 응답. REQUESTED를 완료로 오인하지 않도록 상태와 식별자를 보존한다. */
public record PortOneCancelResult(
        String status,
        String cancellationId,
        BigDecimal cancelledAmount
) {

    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String REQUESTED = "REQUESTED";

    /** PG 취소가 확정된 경우만 true. REQUESTED는 아직 완료가 아니다. */
    public boolean isSucceeded() {
        return SUCCEEDED.equalsIgnoreCase(status);
    }

    public boolean isPending() {
        return REQUESTED.equalsIgnoreCase(status);
    }
}
