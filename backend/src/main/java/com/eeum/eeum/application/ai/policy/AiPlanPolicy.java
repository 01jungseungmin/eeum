package com.eeum.eeum.application.ai.policy;

import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;

// 플랜별 기능 게이팅 정책 — 서비스 레이어에서 이 클래스를 통해서만 검증한다
@Component
public class AiPlanPolicy {

    private static final int BASIC_MONTHLY_LIMIT = 30;

    public void validateAccess(AiPlanType plan, AiFeature feature) {
        if (!plan.isAtLeast(feature.getRequiredPlan())) {
            throw new BusinessException(ErrorCode.AI_PLAN_REQUIRED);
        }
    }

    public boolean isUsageCounted(AiFeature feature) {
        return feature.isUsageCounted();
    }

    // null이면 무제한 (Pro)
    public Integer monthlyLimit(AiPlanType plan) {
        return switch (plan) {
            case FREE -> 0;
            case BASIC -> BASIC_MONTHLY_LIMIT;
            case PRO -> null;
        };
    }

    public List<String> featureDescriptions(AiPlanType plan) {
        return switch (plan) {
            case FREE -> List.of(
                    "기본 상품·이벤트 관리",
                    "대시보드·고객 케어 카드 조회",
                    "공지 직접 작성·등록"
            );
            case BASIC -> List.of(
                    "AI 추천 월 " + BASIC_MONTHLY_LIMIT + "회",
                    "이벤트 추천·홍보 문구 생성",
                    "기본 생활권 매칭 분석",
                    "운영 위험 조기정보 요약",
                    "AI 챗봇"
            );
            case PRO -> List.of(
                    "AI 고객 케어 고급 분석",
                    "생활권 매칭 우선 노출",
                    "리뷰/문의 위험 신호 분석",
                    "에너지·안전 리스크 고급 분석",
                    "절감 계획·전력 사용 리포트",
                    "AI 활동 요약 리포트",
                    "우선 고객 지원"
            );
        };
    }
}
