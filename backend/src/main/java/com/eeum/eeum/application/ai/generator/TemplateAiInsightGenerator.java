package com.eeum.eeum.application.ai.generator;

import com.eeum.eeum.domain.ai.enums.AiRiskLevel;
import org.springframework.stereotype.Component;

import java.util.List;

// 1차 MVP: 실제 LLM 호출 없이 템플릿 기반으로 분석/판단성 텍스트 생성
@Component
public class TemplateAiInsightGenerator implements AiInsightGenerator {

    @Override
    public String eventPerformanceSummary(String storeName, long orderCount, Double conversionRate, Double newCustomerRatio) {
        if (orderCount == 0) {
            return "아직 이벤트 성과 데이터가 충분하지 않습니다. 이벤트를 진행하면 성과 요약을 확인할 수 있어요.";
        }
        StringBuilder summary = new StringBuilder("이벤트 기간 동안 주문 " + orderCount + "건이 발생했습니다.");
        if (newCustomerRatio != null && newCustomerRatio > 0) {
            summary.append(" 신규 고객 비중은 ").append(Math.round(newCustomerRatio * 100)).append("%입니다.");
        }
        summary.append(" 반응이 좋았던 시간대를 중심으로 다음 이벤트를 준비해보세요.");
        return summary.toString();
    }

    @Override
    public String nextEventReason(String storeName, String productName, String timeRange) {
        if (productName == null) {
            return "주문 데이터가 쌓이면 반응이 좋은 상품 기준으로 다음 이벤트를 추천해드릴게요.";
        }
        return "'" + productName + "'의 최근 주문 반응이 좋아 " + timeRange + " 시간대 이벤트를 추천드립니다.";
    }

    @Override
    public String localMatchReason(String storeName, String interest, double radiusKm, int targetCount) {
        if (targetCount == 0) {
            return "아직 매칭 데이터가 충분하지 않습니다. 주문·단골 데이터가 쌓이면 생활권 분석이 정교해집니다.";
        }
        String interestLabel = (interest == null || interest.isBlank()) ? "우리 가게 카테고리" : interest;
        return "반경 " + radiusKm + "km 내 " + interestLabel + " 관심 고객과 단골 활동 데이터를 기준으로 약 "
                + targetCount + "명에게 노출 효과가 기대됩니다.";
    }

    @Override
    public String riskJudgement(String storeName, AiRiskLevel level, boolean hasOwnerInput) {
        String base = switch (level) {
            case WARNING -> "운영 지표에 주의가 필요한 신호가 감지되었습니다. 대응 체크리스트를 확인해주세요.";
            case CAUTION -> "일부 지표에 변동 신호가 있습니다. 미리 점검해두시면 좋겠습니다.";
            case NORMAL -> "현재 특별한 위험 신호는 없습니다. 평소처럼 운영하셔도 좋습니다.";
        };
        if (!hasOwnerInput) {
            base += " 실측값을 입력하시면 더 정확한 진단을 드릴 수 있어요.";
        }
        return base;
    }

    @Override
    public List<String> safetyChecklist(String storeName) {
        return List.of(
                "냉방·공조 설비 필터 청소 상태 확인",
                "냉장·냉동고 온도 및 문 밀폐 상태 점검",
                "조리 설비 주변 가연물 정리",
                "전기 배선·멀티탭 과부하 여부 확인",
                "소화기 위치 및 사용기한 점검"
        );
    }

    @Override
    public String electricityDiagnosis(String storeName, boolean hasOwnerInput) {
        if (!hasOwnerInput) {
            return "실측값이 없어 지역 평균 기반 추정만 가능합니다. 월 전력 사용량을 입력하시면 설비별 진단을 제공해드릴게요.";
        }
        return "입력해주신 사용량 기준으로 볼 때 냉방·냉장 설비 관리가 절감 효과가 가장 큽니다. 절감 계획을 확인해보세요.";
    }

    @Override
    public String activityHighlight(String storeName, long draftCount, long sentCount) {
        if (draftCount == 0 && sentCount == 0) {
            return "이번 기간에는 AI 활동 내역이 없습니다. AI 매니저 기능을 사용해보세요.";
        }
        return "AI 매니저가 초안 " + draftCount + "건을 준비했고, 그중 " + sentCount + "건이 발송되었습니다.";
    }
}
