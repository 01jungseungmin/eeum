package com.eeum.eeum.application.order.dto.response;

import java.math.BigDecimal;

/**
 * PortOne 취소 API 응답.
 *
 * <p>취소 요청이 곧 취소 완료가 아니다. PortOne은 {@code SUCCEEDED} 외에
 * {@code REQUESTED}(승인 대기)와 {@code FAILED}를 반환하며, {@code REQUESTED}는
 * Webhook이나 결제 재조회로 최종 상태를 확정해야 한다. 응답 본문을 버리면
 * 이 구분이 사라져 미완료 취소를 완료로 확정하게 된다.
 *
 * @param status           PortOne 취소 상태 (SUCCEEDED / REQUESTED / FAILED)
 * @param cancellationId   취소 식별자. 재조회·대사에 쓴다
 * @param cancelledAmount  이번 요청으로 취소된 금액. 응답에서 읽지 못하면 null이다
 *
 * <p>누적 취소 금액은 담지 않는다 — 부분 취소가 범위 밖이라 쓰이는 곳이 없고,
 * 항상 null인 필드를 두면 "채워져 있다"고 오해할 여지만 남는다.
 */
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
