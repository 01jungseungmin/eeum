package com.eeum.eeum.domain.reservation.enums;

public enum VisitReservationStatus {
    PENDING,    // 예약 요청
    APPROVED,   // 사장 승인
    REJECTED,   // 사장 거절
    CANCELED,   // 사용자 취소
    COMPLETED   // 방문 완료
}