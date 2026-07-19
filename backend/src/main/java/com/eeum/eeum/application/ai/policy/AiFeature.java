package com.eeum.eeum.application.ai.policy;

import com.eeum.eeum.domain.ai.enums.AiPlanType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 매니저 기능별 최소 플랜 + 월 사용량 카운트 여부.
 *
 * 정책 기준:
 * - FREE: 기본 조회/관리 + AI 문구 생성 체험 월 5회
 * - BASIC: 문구 생성, 답글 초안, 기본 분석, 기본 챗봇 월 50회
 * - PRO: 고급 분석, 우선 노출, 에너지/절감 리포트, 활동 리포트 월 200회
 *
 * 어떤 API가 어느 플랜부터 열리는지는 이 enum 한 곳에서만 관리한다.
 */
@Getter
@RequiredArgsConstructor
public enum AiFeature {

    // ========================
    // FREE — 기본 조회/관리 기능
    // ========================
    DASHBOARD(AiPlanType.FREE, false),
    CUSTOMER_CARE_VIEW(AiPlanType.FREE, false),
    MARKETING_VIEW(AiPlanType.FREE, false),
    REVIEW_INQUIRY_VIEW(AiPlanType.FREE, false),
    EVENT_PERFORMANCE_VIEW(AiPlanType.FREE, false),
    GENERATED_MESSAGE_MANAGE(AiPlanType.FREE, false),
    NOTICE_MANAGE(AiPlanType.FREE, false),
    PLAN_VIEW(AiPlanType.FREE, false),

    // ========================
    // FREE — AI 체험 생성 기능
    // 월 5회 한도 내에서만 사용 가능
    // ========================
    PRODUCT_DESCRIPTION_DRAFT(AiPlanType.FREE, true),
    EVENT_PROMOTION_DRAFT(AiPlanType.FREE, true),
    NOTICE_DRAFT(AiPlanType.FREE, true),

    // ========================
    // BASIC — 기본 AI 분석/추천 기능
    // ========================
    LOCAL_MATCH_VIEW(AiPlanType.BASIC, false),
    OPERATION_RISK_SUMMARY(AiPlanType.BASIC, false),
    ACTIVITY_SUMMARY(AiPlanType.BASIC, false),
    CHATBOT(AiPlanType.BASIC, false),

    // ========================
    // BASIC — 사용량 카운트 대상 생성 기능
    // 월 50회 한도
    // ========================
    CUSTOMER_CARE_DRAFT(AiPlanType.BASIC, true),
    REVIEW_REPLY_DRAFT(AiPlanType.BASIC, true),
    INQUIRY_REPLY_DRAFT(AiPlanType.BASIC, true),
    COMPLAINT_DRAFT(AiPlanType.BASIC, true),
    MARKETING_DRAFT(AiPlanType.BASIC, true),
    CHATBOT_GENERATION(AiPlanType.BASIC, true),

    // ========================
    // PRO — 고급 분석/노출/리포트 기능
    // ========================
    CUSTOMER_CARE_ADVANCED_ANALYSIS(AiPlanType.PRO, false),
    LOCAL_MATCH_EXPOSURE(AiPlanType.PRO, false),
    REVIEW_INQUIRY_RISK_ANALYSIS(AiPlanType.PRO, false),
    OPERATION_RISK_DETAIL(AiPlanType.PRO, false),
    ENERGY_SAFETY_RISK_ANALYSIS(AiPlanType.PRO, false),
    SAVING_PLAN(AiPlanType.PRO, false),
    ELECTRICITY_REPORT(AiPlanType.PRO, false),
    AI_ACTIVITY_REPORT(AiPlanType.PRO, false);

    private final AiPlanType requiredPlan;
    private final boolean usageCounted;
}