package com.eeum.eeum.domain.ai.enums;

public enum AiMessageStatus {
    DRAFT,      // 초안
    REVIEWED,   // 검토 완료
    SENT,       // 발송 완료 (1차에서는 DB 상태 변경만)
    SCHEDULED,  // 예약 발송 대기
    FAILED,     // 발송 실패
    CANCELLED   // 취소
}
