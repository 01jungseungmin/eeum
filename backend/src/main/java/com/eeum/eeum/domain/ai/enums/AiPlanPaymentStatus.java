package com.eeum.eeum.domain.ai.enums;

public enum AiPlanPaymentStatus {
    PENDING,   // 결제 요청 생성
    PAID,      // 결제 완료 — 플랜 반영됨
    FAILED,    // 결제 실패
    CANCELLED  // 결제 취소
}
