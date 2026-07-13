package com.eeum.eeum.application.ai.client;

public enum AiTaskType {
    PRODUCT_DESCRIPTION,    // 상품 설명 생성
    EVENT_MARKETING_COPY,   // 이벤트 홍보 문구 생성
    STORE_NOTICE_DRAFT,     // 가게 공지 초안 생성
    OWNER_REPLY_DRAFT,      // 리뷰 답글/문의 답변 등 사장 답변 초안
    POLICY_CLASSIFICATION,  // 커뮤니티/채팅 정책 분류
    OUTPUT_REVIEW,          // AI 생성 결과 검수
    CHATBOT_REPLY,          // AI 챗봇 답변
    INSIGHT_SUMMARY         // 성과/위험 등 분석 요약
}
