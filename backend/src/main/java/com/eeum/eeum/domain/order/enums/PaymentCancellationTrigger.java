package com.eeum.eeum.domain.order.enums;

/** 전액 취소를 요청한 경로. 네 경로가 같은 처리 서비스로 수렴하므로 구분을 값으로 남긴다. */
public enum PaymentCancellationTrigger {

    /** 고객이 직접 결제를 취소 */
    CUSTOMER_CANCEL,

    /** 사장이 환불 요청을 승인 */
    OWNER_REFUND_APPROVAL,

    /** 사장이 주문을 거절 */
    OWNER_ORDER_REJECT,

    /** PortOne 외부 취소 Webhook 수신 — PG는 이미 취소된 상태로 들어온다 */
    PORTONE_WEBHOOK
}
