package com.eeum.eeum.application.ai.policy;

import com.eeum.eeum.domain.ai.enums.AiPlanType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 매니저 기능별 최소 플랜 + 월 사용량 카운트 여부.
 * 어떤 API가 어느 플랜부터 열리는지는 이 enum 한 곳에서만 관리한다.
 */
@Getter
@RequiredArgsConstructor
public enum AiFeature {

    // 조회성 기능
    DASHBOARD(AiPlanType.FREE, false),
    CUSTOMER_CARE_VIEW(AiPlanType.FREE, false),
    MARKETING_VIEW(AiPlanType.FREE, false),
    REVIEW_INQUIRY_VIEW(AiPlanType.FREE, false),
    EVENT_PERFORMANCE_VIEW(AiPlanType.FREE, false),
    GENERATED_MESSAGE_MANAGE(AiPlanType.FREE, false),
    NOTICE_MANAGE(AiPlanType.FREE, false), // 공지 등록/예약 — Free는 직접 작성 허용
    LOCAL_MATCH_VIEW(AiPlanType.BASIC, false),
    OPERATION_RISK_SUMMARY(AiPlanType.BASIC, false),
    OPERATION_RISK_DETAIL(AiPlanType.PRO, false),
    ACTIVITY_SUMMARY(AiPlanType.BASIC, false),
    PLAN_VIEW(AiPlanType.FREE, false),
    CHATBOT(AiPlanType.BASIC, false),

    // 생성성 기능 — Basic 월 30회 합산 카운트 대상
    CUSTOMER_CARE_DRAFT(AiPlanType.BASIC, true),
    REVIEW_REPLY_DRAFT(AiPlanType.BASIC, true),
    INQUIRY_REPLY_DRAFT(AiPlanType.BASIC, true),
    COMPLAINT_DRAFT(AiPlanType.BASIC, true),
    MARKETING_DRAFT(AiPlanType.BASIC, true),
    NOTICE_DRAFT(AiPlanType.BASIC, true),
    CHATBOT_GENERATION(AiPlanType.BASIC, true),

    // Pro 전용 기능
    LOCAL_MATCH_EXPOSURE(AiPlanType.PRO, false),
    SAVING_PLAN(AiPlanType.PRO, false),
    ELECTRICITY_REPORT(AiPlanType.PRO, false);

    private final AiPlanType requiredPlan;
    private final boolean usageCounted;
}
