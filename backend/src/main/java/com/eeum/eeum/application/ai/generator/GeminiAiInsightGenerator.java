package com.eeum.eeum.application.ai.generator;

import com.eeum.eeum.application.ai.client.AiClientException;
import com.eeum.eeum.application.ai.client.AiTaskType;
import com.eeum.eeum.application.ai.router.AiModelRouter;
import com.eeum.eeum.domain.ai.enums.AiRiskLevel;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Supplier;

/**
 * AiInsightGenerator의 실제 LLM 구현체.
 * 숫자 집계는 기존 Service가 수행하고, LLM에는 계산된 값만 전달해 해석 문장만 생성한다.
 * 실패 시 TemplateAiInsightGenerator로 fallback한다.
 */
@Slf4j
public class GeminiAiInsightGenerator implements AiInsightGenerator {

    private static final String SYSTEM_PROMPT = """
            너는 지역 소상공인을 돕는 이음 서비스의 AI 매니저다.
            한국어 존댓말로 답변한다.
            아래 제공된 숫자와 데이터만 근거로 사용한다.
            제공되지 않은 주문 수, 전환율, 리뷰 수 같은 수치를 절대 추측하거나 만들어내지 않는다.
            2~3문장의 해석 문장만 반환한다. 목록이나 제목은 만들지 않는다.""";

    private final AiModelRouter router;
    private final TemplateAiInsightGenerator templateFallback;

    public GeminiAiInsightGenerator(AiModelRouter router, TemplateAiInsightGenerator templateFallback) {
        this.router = router;
        this.templateFallback = templateFallback;
    }

    @Override
    public String eventPerformanceSummary(String storeName, long orderCount, Double conversionRate, Double newCustomerRatio) {
        String prompt = """
                가게 '%s'의 이벤트 성과를 사장님께 요약해 드린다.
                이벤트 기간 주문 수: %d건
                주문 전환율: %s
                신규 고객 비중: %s""".formatted(
                storeName, orderCount,
                conversionRate != null ? Math.round(conversionRate * 100) + "%" : "집계 불가",
                newCustomerRatio != null ? Math.round(newCustomerRatio * 100) + "%" : "집계 불가");
        return generate("eventPerformanceSummary", prompt,
                () -> templateFallback.eventPerformanceSummary(storeName, orderCount, conversionRate, newCustomerRatio));
    }

    @Override
    public String nextEventReason(String storeName, String productName, String timeRange) {
        String prompt = """
                가게 '%s'의 다음 이벤트 추천 이유를 1~2문장으로 작성한다.
                추천 상품: %s
                추천 시간대: %s""".formatted(
                storeName, productName != null ? productName : "데이터 부족", timeRange);
        return generate("nextEventReason", prompt,
                () -> templateFallback.nextEventReason(storeName, productName, timeRange));
    }

    @Override
    public String localMatchReason(String storeName, String interest, double radiusKm, int targetCount) {
        String prompt = """
                가게 '%s'의 생활권 매칭 분석 이유를 1~2문장으로 작성한다.
                관심사: %s
                노출 반경: %.1fkm
                예상 노출 대상 고객 수: %d명""".formatted(
                storeName, interest != null ? interest : "가게 카테고리", radiusKm, targetCount);
        return generate("localMatchReason", prompt,
                () -> templateFallback.localMatchReason(storeName, interest, radiusKm, targetCount));
    }

    @Override
    public String riskJudgement(String storeName, AiRiskLevel level, boolean hasOwnerInput) {
        String prompt = """
                가게 '%s'의 운영 위험 신호에 대한 AI 판단 문구를 2문장 이내로 작성한다.
                종합 위험 신호: %s
                실측값 입력 여부: %s
                위험 신호가 NORMAL이면 안심시키고, CAUTION/WARNING이면 점검을 권한다.""".formatted(
                storeName, level.name(), hasOwnerInput ? "입력됨" : "미입력");
        return generate("riskJudgement", prompt,
                () -> templateFallback.riskJudgement(storeName, level, hasOwnerInput));
    }

    // 체크리스트는 구조화 목록이라 파싱 리스크가 커 1.5차에서는 Template을 그대로 사용
    @Override
    public List<String> safetyChecklist(String storeName) {
        return templateFallback.safetyChecklist(storeName);
    }

    @Override
    public String electricityDiagnosis(String storeName, boolean hasOwnerInput) {
        String prompt = """
                가게 '%s'의 전력 사용 리포트 핵심 진단을 2문장 이내로 작성한다.
                실측값 입력 여부: %s
                실측값이 없으면 입력을 권유하고, 있으면 냉방/냉장 설비 관리 중심으로 절감을 권한다.""".formatted(
                storeName, hasOwnerInput ? "입력됨" : "미입력");
        return generate("electricityDiagnosis", prompt,
                () -> templateFallback.electricityDiagnosis(storeName, hasOwnerInput));
    }

    @Override
    public String activityHighlight(String storeName, long draftCount, long sentCount) {
        String prompt = """
                가게 '%s'의 AI 매니저 활동 요약 하이라이트를 1~2문장으로 작성한다.
                최근 30일 AI 초안 생성 수: %d건
                발송 처리 수: %d건""".formatted(storeName, draftCount, sentCount);
        return generate("activityHighlight", prompt,
                () -> templateFallback.activityHighlight(storeName, draftCount, sentCount));
    }

    private String generate(String method, String prompt, Supplier<String> fallbackSupplier) {
        try {
            return router.generate(AiTaskType.INSIGHT_SUMMARY, SYSTEM_PROMPT, prompt).content();
        } catch (AiClientException e) {
            log.warn("[AI-GENERATOR] LLM 분석 문구 생성 실패 — Template fallback 사용 (method={}): {}",
                    method, e.getMessage());
            return fallbackSupplier.get();
        }
    }
}
