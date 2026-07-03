package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiOwnerMetricInputRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiOperationRiskResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiSavingPlanResponseDto;
import com.eeum.eeum.application.ai.generator.TemplateAiInsightGenerator;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiOwnerMetricInput;
import com.eeum.eeum.domain.ai.entity.AiSavingPlan;
import com.eeum.eeum.domain.ai.enums.AiDataSourceType;
import com.eeum.eeum.domain.ai.enums.AiMetricType;
import com.eeum.eeum.domain.ai.enums.AiRiskLevel;
import com.eeum.eeum.domain.ai.enums.AiSavingPlanStatus;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiOwnerMetricInputRepository;
import com.eeum.eeum.domain.ai.repository.AiSavingPlanRepository;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiOperationRiskServiceTest {

    @InjectMocks
    private AiOperationRiskService operationRiskService;

    @Mock private AiManagerSupportService supportService;
    @Mock private AiOwnerMetricInputRepository aiOwnerMetricInputRepository;
    @Mock private AiSavingPlanRepository aiSavingPlanRepository;
    @Mock private AiActionLogRepository aiActionLogRepository;
    @Mock private StoreReviewRepository storeReviewRepository;
    @Mock private InquiryRepository inquiryRepository;
    @Spy private TemplateAiInsightGenerator aiInsightGenerator = new TemplateAiInsightGenerator();

    private static final Long OWNER_ID = 100L;
    private static final Long STORE_ID = 1L;

    // ──────────────────── Helpers ────────────────────

    private Store stubStore() {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(STORE_ID);
        lenient().when(store.getName()).thenReturn("테스트 상점");
        lenient().when(store.getAccount()).thenReturn(mock(Account.class));
        when(supportService.getOwnerStore(OWNER_ID)).thenReturn(store);
        return store;
    }

    private void stubNormalSignals() {
        when(storeReviewRepository.findByStore_StoreIdAndCreatedAtAfter(anyLong(), any())).thenReturn(List.of());
        when(inquiryRepository.countByStore_StoreIdAndStatus(anyLong(), any())).thenReturn(0L);
    }

    // ──────────────────── Tests ────────────────────

    @Test
    void 운영_위험_정보_조회에_성공한다() {
        // given
        stubStore();
        stubNormalSignals();
        when(aiOwnerMetricInputRepository.existsByStore_StoreId(STORE_ID)).thenReturn(false);
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(false);

        // when
        AiOperationRiskResponseDto response = operationRiskService.getRisks(OWNER_ID);

        // then
        assertThat(response.getOverallRiskLevel()).isEqualTo(AiRiskLevel.NORMAL);
        assertThat(response.getAiJudgement()).isNotBlank();
        assertThat(response.getDataSources())
                .extracting("sourceType")
                .containsExactly(AiDataSourceType.LOCAL_AVERAGE_ONLY);
        assertThat(response.getSourceType()).isEqualTo(AiDataSourceType.LOCAL_AVERAGE_ONLY);
        assertThat(response.isHasPreciseData()).isFalse();
        assertThat(response.getNotice()).contains("추정 분석");
        assertThat(response.isHasSavedPlan()).isFalse();
    }

    @Test
    void 사장님_실측값_입력_후에는_sourceType이_OWNER_INPUT으로_반영된다() {
        // given
        stubStore();
        stubNormalSignals();
        when(aiOwnerMetricInputRepository.existsByStore_StoreId(STORE_ID)).thenReturn(true);
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(false);

        // when
        AiOperationRiskResponseDto response = operationRiskService.getRisks(OWNER_ID);

        // then
        assertThat(response.getDataSources())
                .extracting("sourceType")
                .containsExactly(AiDataSourceType.OWNER_INPUT);
        assertThat(response.getSourceType()).isEqualTo(AiDataSourceType.OWNER_INPUT);
        assertThat(response.isHasPreciseData()).isTrue();
    }

    @Test
    void 실측값_신규_입력_시_저장되고_액션_로그가_기록된다() {
        // given
        stubStore();
        when(aiOwnerMetricInputRepository.findByStore_StoreIdAndMetricTypeAndYearMonth(
                anyLong(), any(), anyString())).thenReturn(Optional.empty());

        // when
        operationRiskService.saveOwnerInput(OWNER_ID, new AiOwnerMetricInputRequestDto(
                AiMetricType.MONTHLY_POWER_KWH, new BigDecimal("850.5"), "2026-07"));

        // then
        verify(aiOwnerMetricInputRepository).save(any(AiOwnerMetricInput.class));
        verify(aiActionLogRepository).save(any());
    }

    @Test
    void 절감_계획_생성에_성공하면_항목_3개가_포함된다() {
        // given
        stubStore();
        when(aiOwnerMetricInputRepository.findByStore_StoreIdAndMetricTypeAndYearMonth(
                anyLong(), any(), anyString())).thenReturn(Optional.empty());
        when(aiSavingPlanRepository.save(any(AiSavingPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        AiSavingPlanResponseDto response = operationRiskService.createSavingPlan(OWNER_ID);

        // then
        assertThat(response.getItems()).hasSize(3);
        assertThat(response.getStatus()).isEqualTo(AiSavingPlanStatus.DRAFT);
        // 실측값이 없으면 절감액은 null (하드코딩 금지)
        assertThat(response.getTotalExpectedSavingAmount()).isNull();
    }

    @Test
    void 절감_계획_저장_후_운영_위험_조회의_hasSavedPlan이_true가_된다() {
        // given
        Store store = stubStore();
        stubNormalSignals();
        AiSavingPlan plan = AiSavingPlan.create(store, "이번 달 전력 절감 계획", null);
        when(aiSavingPlanRepository.findFirstByStore_StoreIdOrderByCreatedAtDesc(STORE_ID))
                .thenReturn(Optional.of(plan));

        // when
        AiSavingPlanResponseDto saved = operationRiskService.saveSavingPlan(OWNER_ID, null);

        // then
        assertThat(saved.getStatus()).isEqualTo(AiSavingPlanStatus.SAVED);

        // 저장 이후 조회 시 hasSavedPlan = true
        when(aiOwnerMetricInputRepository.existsByStore_StoreId(STORE_ID)).thenReturn(false);
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(true);
        AiOperationRiskResponseDto risk = operationRiskService.getRisks(OWNER_ID);
        assertThat(risk.isHasSavedPlan()).isTrue();
    }

    @Test
    void 생성된_절감_계획이_없을_때_저장하면_AI_SAVING_PLAN_NOT_FOUND_예외가_발생한다() {
        // given
        stubStore();
        when(aiSavingPlanRepository.findFirstByStore_StoreIdOrderByCreatedAtDesc(STORE_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> operationRiskService.saveSavingPlan(OWNER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_SAVING_PLAN_NOT_FOUND);
    }
}
