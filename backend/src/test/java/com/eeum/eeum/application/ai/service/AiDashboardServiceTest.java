package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiActivitySummaryResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiCustomerCareCardDto;
import com.eeum.eeum.application.ai.dto.response.AiEventPerformanceResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiManagerDashboardResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiOperationRiskResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiReviewInquiryResponseDto;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.domain.ai.enums.AiCareType;
import com.eeum.eeum.domain.ai.enums.AiPlanType;
import com.eeum.eeum.domain.ai.enums.AiRiskLevel;
import com.eeum.eeum.domain.store.entity.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiDashboardServiceTest {

    @InjectMocks
    private AiDashboardService aiDashboardService;

    @Mock private AiManagerSupportService supportService;
    @Mock private AiCustomerCareService customerCareService;
    @Mock private AiReviewInquiryService reviewInquiryService;
    @Mock private AiEventPerformanceService eventPerformanceService;
    @Mock private AiLocalMatchService localMatchService;
    @Mock private AiOperationRiskService operationRiskService;
    @Mock private AiActivityService activityService;

    private static final Long OWNER_ID = 100L;

    private Store stubStore(Long storeId) {
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(storeId);
        when(supportService.getOwnerStore(OWNER_ID)).thenReturn(store);
        return store;
    }

    private void stubBasicPlan(Long storeId) {
        when(supportService.getPlanType(storeId)).thenReturn(AiPlanType.BASIC);
    }

    private void stubFreePlan(Long storeId) {
        when(supportService.getPlanType(storeId)).thenReturn(AiPlanType.FREE);
    }

    private AiReviewInquiryResponseDto emptyReviewInquiry() {
        return AiReviewInquiryResponseDto.builder()
                .unansweredReviewCount(0)
                .unansweredInquiryCount(0)
                .complaintKeywords(List.of())
                .unansweredReviews(List.of())
                .unansweredInquiries(List.of())
                .hasData(false)
                .build();
    }

    private AiEventPerformanceResponseDto emptyEventPerformance() {
        return AiEventPerformanceResponseDto.builder()
                .eventOrderCount(0)
                .regularReorderCount(0)
                .hasData(false)
                .emptyMessage("이벤트 없음")
                .build();
    }

    @Test
    void FREE_플랜은_activitySummary가_null로_응답된다() {
        // given
        Store store = stubStore(1L);
        stubFreePlan(1L);
        when(customerCareService.getCareCards(OWNER_ID)).thenReturn(List.of());
        when(reviewInquiryService.getOverview(OWNER_ID)).thenReturn(emptyReviewInquiry());
        when(eventPerformanceService.getPerformance(OWNER_ID)).thenReturn(emptyEventPerformance());

        // when
        AiManagerDashboardResponseDto result = aiDashboardService.getDashboard(OWNER_ID);

        // then
        assertThat(result.getActivitySummary()).isNull();
        assertThat(result.getLocalMatchScore()).isNull();
        assertThat(result.getOperationRiskSummary()).isNull();
        verify(activityService, never()).getActivitySummary(any());
    }

    @Test
    void BASIC_플랜은_sentCount가_하드코딩_0이_아닌_실제_값으로_응답된다() {
        // given
        Store store = stubStore(1L);
        stubBasicPlan(1L);
        when(customerCareService.getCareCards(OWNER_ID)).thenReturn(List.of());
        when(reviewInquiryService.getOverview(OWNER_ID)).thenReturn(emptyReviewInquiry());
        when(eventPerformanceService.getPerformance(OWNER_ID)).thenReturn(emptyEventPerformance());

        AiActivitySummaryResponseDto activitySummary = AiActivitySummaryResponseDto.builder()
                .period("최근 30일")
                .reviewReplyDraftCount(2)
                .inquiryReplyDraftCount(1)
                .regularMessageCount(0)
                .inactiveAlertCount(0)
                .draftCount(5)
                .sentCount(3)
                .unansweredInquiryRemainingCount(0)
                .highlight("하이라이트")
                .build();
        when(activityService.getActivitySummary(OWNER_ID)).thenReturn(activitySummary);

        AiOperationRiskResponseDto riskResponse = AiOperationRiskResponseDto.builder()
                .overallRiskLevel(AiRiskLevel.NORMAL)
                .aiJudgement("정상")
                .build();
        when(operationRiskService.getRisks(OWNER_ID)).thenReturn(riskResponse);

        when(localMatchService.getLocalMatch(OWNER_ID)).thenReturn(
                com.eeum.eeum.application.ai.dto.response.AiLocalMatchResponseDto.builder()
                        .totalScore(80)
                        .segments(List.of())
                        .hasData(true)
                        .build());

        // when
        AiManagerDashboardResponseDto result = aiDashboardService.getDashboard(OWNER_ID);

        // then
        assertThat(result.getActivitySummary()).isNotNull();
        assertThat(result.getActivitySummary().getSentCount()).isEqualTo(3);
        assertThat(result.getActivitySummary().getDraftCount()).isEqualTo(5);
    }

    @Test
    void todoCount는_미답변_리뷰와_문의와_전송가능_케어카드_합산이다() {
        // given
        Store store = stubStore(1L);
        stubFreePlan(1L);

        AiCustomerCareCardDto sendableCard = AiCustomerCareCardDto.builder()
                .careType(AiCareType.CART_INTEREST)
                .title("제목")
                .targetCustomerCount(5)
                .sendable(true)
                .build();
        AiCustomerCareCardDto notSendableCard = AiCustomerCareCardDto.builder()
                .careType(AiCareType.INACTIVE_REGULAR)
                .title("제목2")
                .targetCustomerCount(0)
                .sendable(false)
                .build();
        when(customerCareService.getCareCards(OWNER_ID)).thenReturn(List.of(sendableCard, notSendableCard));

        AiReviewInquiryResponseDto reviewInquiry = AiReviewInquiryResponseDto.builder()
                .unansweredReviewCount(2)
                .unansweredInquiryCount(1)
                .complaintKeywords(List.of())
                .unansweredReviews(List.of())
                .unansweredInquiries(List.of())
                .hasData(true)
                .build();
        when(reviewInquiryService.getOverview(OWNER_ID)).thenReturn(reviewInquiry);
        when(eventPerformanceService.getPerformance(OWNER_ID)).thenReturn(emptyEventPerformance());

        // when
        AiManagerDashboardResponseDto result = aiDashboardService.getDashboard(OWNER_ID);

        // then — 미답변 리뷰 2 + 미답변 문의 1 + sendable 카드 1 = 4
        assertThat(result.getTodoCount()).isEqualTo(4);
    }
}
