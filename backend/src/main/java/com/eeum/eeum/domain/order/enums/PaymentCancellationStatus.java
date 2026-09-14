package com.eeum.eeum.domain.order.enums;

/**
 * 전액 취소 작업의 진행 단계.
 *
 * 외부 PG 취소는 롤백되지 않는다. 그래서 "PG를 취소했는가"와 "내부에 반영했는가"를
 * 한 트랜잭션의 성패로 뭉뚱그리지 않고 단계로 나눠 기록한다. 내부 반영이 실패하면
 * 롤백이 아니라 #MANUAL_REVIEW_REQUIRED로 남겨 사람이 수습할 수 있게 한다.
 */
public enum PaymentCancellationStatus {

    /** 취소 대상으로 확정하고 외부 호출을 준비한 상태 */
    PENDING,

    /** PortOne 취소를 요청했고 응답을 기다리는 상태 */
    PG_CANCEL_REQUESTED,

    /** PortOne이 SUCCEEDED로 확정했으나 내부 반영은 아직인 상태 */
    PG_CANCELLED,

    /** 내부 결제·주문·정산 반영까지 끝난 상태 */
    COMPLETED,

    /**
     * 사람이 확인해야 하는 상태.
     *
     * PG는 취소됐는데 내부 반영이 실패했거나, PortOne이 REQUESTED로 응답해
     * 최종 상태가 확정되지 않았거나, 지급이 이미 시작된 정산을 건드려야 하는 경우다.
     */
    MANUAL_REVIEW_REQUIRED
}
