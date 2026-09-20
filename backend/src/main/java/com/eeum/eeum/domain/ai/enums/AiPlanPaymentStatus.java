package com.eeum.eeum.domain.ai.enums;

public enum AiPlanPaymentStatus {
    PENDING,   // 결제 요청 생성
    PAID,      // 결제 완료 — 플랜 반영됨
    FAILED,    // 결제 실패
    PARTIALLY_CANCELLED, // 부분 환불 — 유료 권한은 유지, 운영 대사 대상
    CANCELLED  // 결제 취소
}
