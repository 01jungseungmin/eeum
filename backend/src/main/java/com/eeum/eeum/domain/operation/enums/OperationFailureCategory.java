package com.eeum.eeum.domain.operation.enums;

/**
 * 운영 실패 이력의 분류.
 *
 * <p>관리자 대시보드의 상단 필터 탭과 1:1로 대응한다.
 * 새 값을 추가하면 프론트 필터 옵션도 함께 갱신해야 한다.
 */
public enum OperationFailureCategory {
    PAYMENT_WEBHOOK,  // PortOne Webhook 수신·검증·처리 실패
    REFUND,           // 환불/결제 취소 실패
    SCHEDULER,        // 스케줄러 작업 실패
    EXTERNAL_API      // 외부 API 호출 실패 (PortOne 조회, NTS, 카카오 등)
}
