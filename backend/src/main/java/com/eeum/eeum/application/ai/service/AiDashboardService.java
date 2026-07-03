package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiActivitySummaryResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiCustomerCareCardDto;
import com.eeum.eeum.application.ai.dto.response.AiEventPerformanceResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiLocalMatchResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiManagerDashboardResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiOperationRiskResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiReviewInquiryResponseDto;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.store.entity.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiDashboardService {

    private static final String PRIVACY_NOTICE = "고객 동의 범위 내에서 제공되는 관계 신호 기반입니다.";

    private final AiManagerSupportService supportService;
    private final AiCustomerCareService customerCareService;
    private final AiReviewInquiryService reviewInquiryService;
    private final AiEventPerformanceService eventPerformanceService;
    private final AiLocalMatchService localMatchService;
    private final AiOperationRiskService operationRiskService;
    private final AiActivityService activityService;

    // @Transactional 없음 — 각 서브 서비스가 자체 TX를 열고 커밋 후 커넥션 반납.
    // 하나의 TX로 묶으면 AI 텍스트 생성 HTTP 호출 동안 커넥션이 묶여 풀이 고갈된다.
    public AiManagerDashboardResponseDto getDashboard(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.DASHBOARD);
        AiPlanType plan = supportService.getPlanType(store.getStoreId());

        List<AiCustomerCareCardDto> careCards = customerCareService.getCareCards(ownerId);
        AiReviewInquiryResponseDto reviewInquiry = reviewInquiryService.getOverview(ownerId);
        AiEventPerformanceResponseDto eventPerformance = eventPerformanceService.getPerformance(ownerId);

        // 플랜 미달 섹션은 null로 내려 프론트에서 업그레이드 안내를 노출한다
        Integer localMatchScore = null;
        if (plan.isAtLeast(AiPlanType.BASIC)) {
            AiLocalMatchResponseDto localMatch = localMatchService.getLocalMatch(ownerId);
            localMatchScore = localMatch.getTotalScore();
        }

        AiManagerDashboardResponseDto.OperationRiskSummaryDto riskSummary = null;
        if (plan.isAtLeast(AiPlanType.BASIC)) {
            AiOperationRiskResponseDto risk = operationRiskService.getRisks(ownerId);
            riskSummary = AiManagerDashboardResponseDto.OperationRiskSummaryDto.builder()
                    .riskLevel(risk.getOverallRiskLevel())
                    .headline(risk.getAiJudgement())
                    .build();
        }

        AiManagerDashboardResponseDto.ActivitySummaryDto activitySummary = null;
        if (plan.isAtLeast(AiPlanType.BASIC)) {
            AiActivitySummaryResponseDto activity = activityService.getActivitySummary(ownerId);
            activitySummary = AiManagerDashboardResponseDto.ActivitySummaryDto.builder()
                    .draftCount(activity.getDraftCount())
                    .sentCount(activity.getSentCount())
                    .highlight(activity.getHighlight())
                    .build();
        }

        long todoCount = reviewInquiry.getUnansweredReviewCount()
                + reviewInquiry.getUnansweredInquiryCount()
                + careCards.stream().filter(AiCustomerCareCardDto::isSendable).count();

        return AiManagerDashboardResponseDto.builder()
                .reportedAt(LocalDateTime.now())
                .todoCount(todoCount)
                .privacyNotice(PRIVACY_NOTICE)
                .customerCareSummaries(careCards.stream()
                        .map(card -> AiManagerDashboardResponseDto.CustomerCareSummaryDto.builder()
                                .careType(card.getCareType().name())
                                .title(card.getTitle())
                                .targetCustomerCount(card.getTargetCustomerCount())
                                .build())
                        .toList())
                .reviewInquirySummary(AiManagerDashboardResponseDto.ReviewInquirySummaryDto.builder()
                        .unansweredReviewCount(reviewInquiry.getUnansweredReviewCount())
                        .unansweredInquiryCount(reviewInquiry.getUnansweredInquiryCount())
                        .complaintKeywordCount(reviewInquiry.getComplaintKeywords().size())
                        .build())
                .eventPerformanceSummary(AiManagerDashboardResponseDto.EventPerformanceSummaryDto.builder()
                        .productViewCount(eventPerformance.getProductViewCount())
                        .orderConversionRate(eventPerformance.getOrderConversionRate())
                        .newCustomerRatio(eventPerformance.getNewCustomerRatio())
                        .regularReorderCount(eventPerformance.getRegularReorderCount())
                        .build())
                .localMatchScore(localMatchScore)
                .operationRiskSummary(riskSummary)
                .activitySummary(activitySummary)
                .build();
    }
}
