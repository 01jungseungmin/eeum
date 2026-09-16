package com.eeum.eeum.domain.ai.enums;

public enum AiChatActionType {
    OPEN_EVENT_REGISTER,   // 이벤트 등록 화면으로 이동
    OPEN_NOTICE_REGISTER,  // 공지 등록 화면으로 이동
    OPEN_REVIEW_DRAFT,     // 리뷰 답글 초안 화면으로 이동
    OPEN_SAFETY_CHECK,     // 안전 점검 화면으로 이동
    SEND_MESSAGE,          // 생성된 메시지 발송
    REGENERATE             // 문구 재생성
}
