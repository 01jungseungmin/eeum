package com.eeum.eeum.domain.order.enums;

public enum OrderStatus {
    PENDING,     // 주문 생성, 결제 또는 사장 확인 대기
    PAID,        // 온라인 결제 완료
    CONFIRMED,   // 사장 확인
    READY,       // 픽업/수령 준비 완료
    COMPLETED,   // 거래 완료
    CANCELLED,   // 사용자/사장/결제 실패로 취소
    EXPIRED      // 결제 대기 만료
}