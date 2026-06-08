package com.eeum.eeum.domain.order.enums;

public enum PaymentStatus {
    PENDING,    // 온라인 결제 대기
    NOT_PAID,   // 현장결제 미결제
    PAID,       // 결제 완료
    CANCELLED,  // 결제 취소
    FAILED,     // 결제 실패
    REFUNDED    // 결제 환불
}