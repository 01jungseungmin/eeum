package com.eeum.eeum.application.ai.generator;

import com.eeum.eeum.domain.ai.enums.AiRiskLevel;

import java.util.List;

/**
 * 리스크 판단, 매칭 이유, 성과 요약 등 "분석/판단성 텍스트" 생성 인터페이스.
 * 1차 MVP는 Template 구현체 사용 — 추후 실제 LLM 구현체로 교체한다.
 */
public interface AiInsightGenerator {

    String eventPerformanceSummary(String storeName, long orderCount, Double conversionRate, Double newCustomerRatio);

    String nextEventReason(String storeName, String productName, String timeRange);

    String localMatchReason(String storeName, String interest, double radiusKm, int targetCount);

    String riskJudgement(String storeName, AiRiskLevel level, boolean hasOwnerInput);

    List<String> safetyChecklist(String storeName);

    String electricityDiagnosis(String storeName, boolean hasOwnerInput);

    String activityHighlight(String storeName, long draftCount, long sentCount);
}
