package com.eeum.eeum.domain.ai.enums;

public enum AiActionType {
    DRAFT_CREATED,       // 초안 생성
    MESSAGE_SENT,        // 메시지 발송 처리
    MESSAGE_SCHEDULED,   // 메시지 예약 처리
    MESSAGE_CANCELLED,   // 메시지 취소
    NOTICE_PUBLISHED,    // 공지 등록
    EXPOSURE_STARTED,    // 생활권 노출 시작
    EXPOSURE_STOPPED,    // 생활권 노출 중지
    OWNER_METRIC_INPUT,  // 사장님 실측값 입력
    SAVING_PLAN_SAVED,   // 절감 계획 저장
    CHATBOT_ANSWERED     // 챗봇 답변
}
