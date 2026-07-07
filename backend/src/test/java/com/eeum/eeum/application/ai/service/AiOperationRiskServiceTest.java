package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiOwnerMetricInputRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiElectricityReportResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiOperationRiskResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiSavingPlanResponseDto;
import com.eeum.eeum.application.ai.generator.TemplateAiInsightGenerator;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiOwnerMetricInput;
import com.eeum.eeum.domain.ai.enums.AiDataSourceType;
import com.eeum.eeum.domain.ai.enums.AiMetricType;
import com.eeum.eeum.domain.ai.enums.AiRiskLevel;
import com.eeum.eeum.domain.ai.enums.AiSavingPlanStatus;
import com.eeum.eeum.domain.ai.repository.AiOwnerMetricInputRepository;
import com.eeum.eeum.domain.ai.repository.AiSavingPlanRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private AiOwnerMetricCommandExecutor ownerMetricCommandExecutor;
    @Mock private AiSavingPlanCommandExecutor savingPlanCommandExecutor;
    @Mock private com.eeum.eeum.common.service.RedisLockService redisLockService;
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

    // buildSeasonalAlertText/resolveSeasonalAlertLevel이 실제 현재 월(LocalDate.now())을 기준으로 판단하므로,
    // 테스트도 같은 기준으로 기댓값을 계산해 실행 시점(달)에 관계없이 항상 성립하도록 한다.
    private boolean isPeakSeasonNow() {
        int month = LocalDate.now().getMonthValue();
        return (month >= 6 && month <= 8) || month == 12 || month <= 2;
    }

    private void stubMetricInputs(AiOwnerMetricInput... inputs) {
        lenient().when(aiOwnerMetricInputRepository
                        .findByStore_StoreIdAndMetricTypeInAndYearMonthIn(eq(STORE_ID), any(), any()))
                .thenReturn(List.of(inputs));
    }

    private AiOwnerMetricInput metric(Store store, AiMetricType type, BigDecimal value) {
        return AiOwnerMetricInput.create(store, type, value, YearMonth.now().toString());
    }

    private void stubEmptyMonthlyChart() {
        lenient().when(aiOwnerMetricInputRepository.findByStore_StoreIdAndMetricTypeAndYearMonthInOrderByYearMonthAsc(
                        eq(STORE_ID), eq(AiMetricType.MONTHLY_POWER_KWH), any()))
                .thenReturn(List.of());
    }

    // ──────────────────── 기본 조회 ────────────────────

    @Test
    void 실측값이_없으면_에너지_활동_안전_카드는_NORMAL이고_hasPreciseData는_false다() {
        // given
        stubStore();
        stubMetricInputs();
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(false);

        // when
        AiOperationRiskResponseDto response = operationRiskService.getRisks(OWNER_ID);

        // then
        assertThat(response.getEnergySignalLevel()).isEqualTo(AiRiskLevel.NORMAL);
        assertThat(response.getActivityAnomalyLevel()).isEqualTo(AiRiskLevel.NORMAL);
        assertThat(response.getSafetyCheckLevel()).isEqualTo(AiRiskLevel.NORMAL);
        assertThat(response.getOverallRiskLevel())
                .isEqualTo(isPeakSeasonNow() ? AiRiskLevel.CAUTION : AiRiskLevel.NORMAL);
        assertThat(response.getSourceType()).isEqualTo(AiDataSourceType.LOCAL_AVERAGE_ONLY);
        assertThat(response.isHasPreciseData()).isFalse();
        assertThat(response.getNotice()).contains("추정 분석");
        assertThat(response.isHasSavedPlan()).isFalse();
    }

    @Test
    void 전력_사용량이_580_720_구간이면_hasPreciseData는_true이고_활동_이상_카드는_NORMAL이다() {
        // given
        Store store = stubStore();
        stubMetricInputs(metric(store, AiMetricType.MONTHLY_POWER_KWH, new BigDecimal("650")));
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(false);

        // when
        AiOperationRiskResponseDto response = operationRiskService.getRisks(OWNER_ID);

        // then
        assertThat(response.getSourceType()).isEqualTo(AiDataSourceType.OWNER_INPUT);
        assertThat(response.isHasPreciseData()).isTrue();
        assertThat(response.getEnergySignalLevel()).isEqualTo(AiRiskLevel.NORMAL);
        assertThat(response.getActivityAnomalyLevel()).isEqualTo(AiRiskLevel.NORMAL);
    }

    @Test
    void 전력_사용량이_800_이상이면_에너지_신호가_WARNING이고_종합_위험도도_WARNING이다() {
        // given
        Store store = stubStore();
        stubMetricInputs(metric(store, AiMetricType.MONTHLY_POWER_KWH, new BigDecimal("850")));
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(false);

        // when
        AiOperationRiskResponseDto response = operationRiskService.getRisks(OWNER_ID);

        // then
        assertThat(response.getEnergySignalLevel()).isEqualTo(AiRiskLevel.WARNING);
        assertThat(response.getOverallRiskLevel()).isEqualTo(AiRiskLevel.WARNING);
    }

    @Test
    void 전력_사용량이_700_이상_800_미만이면_에너지_신호가_CAUTION이다() {
        // given
        Store store = stubStore();
        stubMetricInputs(metric(store, AiMetricType.MONTHLY_POWER_KWH, new BigDecimal("720")));
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(false);

        // when
        AiOperationRiskResponseDto response = operationRiskService.getRisks(OWNER_ID);

        // then
        assertThat(response.getEnergySignalLevel()).isEqualTo(AiRiskLevel.CAUTION);
    }

    @Test
    void 전력_사용량이_450_미만이면_업종_활동_이상_카드가_CAUTION이다() {
        // given
        Store store = stubStore();
        stubMetricInputs(metric(store, AiMetricType.MONTHLY_POWER_KWH, new BigDecimal("400")));
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(false);

        // when
        AiOperationRiskResponseDto response = operationRiskService.getRisks(OWNER_ID);

        // then
        assertThat(response.getActivityAnomalyLevel()).isEqualTo(AiRiskLevel.CAUTION);
    }

    @Test
    void 가스_설비를_보유하면_안전_리스크_카드가_CAUTION이고_종합_위험도에_반영된다() {
        // given
        Store store = stubStore();
        stubMetricInputs(metric(store, AiMetricType.GAS_EQUIPMENT_COUNT, new BigDecimal("2")));
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(false);

        // when
        AiOperationRiskResponseDto response = operationRiskService.getRisks(OWNER_ID);

        // then
        assertThat(response.getSafetyCheckLevel()).isEqualTo(AiRiskLevel.CAUTION);
        assertThat(response.getOverallRiskLevel().ordinal()).isGreaterThanOrEqualTo(AiRiskLevel.CAUTION.ordinal());
    }

    @Test
    void 실측값_신규_입력_시_저장되고_액션_로그가_기록된다() {
        // given
        Store store = stubStore();
        AiOwnerMetricInputRequestDto request = new AiOwnerMetricInputRequestDto(
                AiMetricType.MONTHLY_POWER_KWH, new BigDecimal("850.5"), "2026-07");
        // Runnable 기반 lock pass-through
        org.mockito.Mockito.doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return null;
        }).when(redisLockService).executeWithLock(anyString(), any(java.time.Duration.class), any(Runnable.class));

        // when
        operationRiskService.saveOwnerInput(OWNER_ID, request);

        // then
        verify(ownerMetricCommandExecutor).upsertMetricInTx(eq(store), eq(OWNER_ID), eq(request));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 절감_계획_생성_시_Executor에게_위임하고_락을_획득한다() {
        // given
        Store store = stubStore();
        AiSavingPlanResponseDto mockResponse = mock(AiSavingPlanResponseDto.class);
        when(redisLockService.executeWithLock(anyString(), any(java.time.Duration.class), any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> ((java.util.function.Supplier<Object>) invocation.getArgument(2)).get());
        when(savingPlanCommandExecutor.createSavingPlanInTx(store)).thenReturn(mockResponse);

        // when
        AiSavingPlanResponseDto response = operationRiskService.createSavingPlan(OWNER_ID);

        // then
        assertThat(response).isEqualTo(mockResponse);
        verify(savingPlanCommandExecutor).createSavingPlanInTx(store);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 절감_계획_저장_후_운영_위험_조회의_hasSavedPlan이_true가_된다() {
        // given
        Store store = stubStore();
        stubMetricInputs();
        AiSavingPlanResponseDto mockSaved = mock(AiSavingPlanResponseDto.class);
        when(mockSaved.getStatus()).thenReturn(AiSavingPlanStatus.SAVED);
        when(redisLockService.executeWithLock(anyString(), any(java.time.Duration.class), any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> ((java.util.function.Supplier<Object>) invocation.getArgument(2)).get());
        when(savingPlanCommandExecutor.savePlanInTx(store, OWNER_ID, null)).thenReturn(mockSaved);

        // when
        AiSavingPlanResponseDto saved = operationRiskService.saveSavingPlan(OWNER_ID, null);

        // then
        assertThat(saved.getStatus()).isEqualTo(AiSavingPlanStatus.SAVED);
        verify(savingPlanCommandExecutor).savePlanInTx(store, OWNER_ID, null);

        // 저장 이후 조회 시 hasSavedPlan = true
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(true);
        AiOperationRiskResponseDto risk = operationRiskService.getRisks(OWNER_ID);
        assertThat(risk.isHasSavedPlan()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 생성된_절감_계획이_없을_때_저장하면_AI_SAVING_PLAN_NOT_FOUND_예외가_발생한다() {
        // given
        Store store = stubStore();
        when(redisLockService.executeWithLock(anyString(), any(java.time.Duration.class), any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> ((java.util.function.Supplier<Object>) invocation.getArgument(2)).get());
        when(savingPlanCommandExecutor.savePlanInTx(store, OWNER_ID, null))
                .thenThrow(new BusinessException(ErrorCode.AI_SAVING_PLAN_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> operationRiskService.saveSavingPlan(OWNER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_SAVING_PLAN_NOT_FOUND);
    }

    // ──────────────────── 전력 사용 리포트 ────────────────────

    @Test
    void 설비_입력이_없으면_equipmentShares는_빈_리스트다() {
        // given
        stubStore();
        stubEmptyMonthlyChart();
        stubMetricInputs();

        // when
        AiElectricityReportResponseDto response = operationRiskService.getElectricityReport(OWNER_ID);

        // then
        assertThat(response.getEquipmentShares()).isEmpty();
        assertThat(response.getEstimatedSavingAmount()).isNull();
    }

    @Test
    void 냉방_냉장_설비_대수가_있으면_equipmentShares가_비중으로_채워진다() {
        // given
        Store store = stubStore();
        stubEmptyMonthlyChart();
        stubMetricInputs(
                metric(store, AiMetricType.MONTHLY_POWER_BILL, new BigDecimal("100000")),
                metric(store, AiMetricType.AIR_CONDITIONER_COUNT, new BigDecimal("2")),
                metric(store, AiMetricType.REFRIGERATOR_COUNT, new BigDecimal("1")));

        // when
        AiElectricityReportResponseDto response = operationRiskService.getElectricityReport(OWNER_ID);

        // then
        assertThat(response.getEquipmentShares()).hasSize(2);
        int totalRatio = response.getEquipmentShares().stream()
                .mapToInt(AiElectricityReportResponseDto.EquipmentShareDto::getRatio)
                .sum();
        assertThat(totalRatio).isEqualTo(100);
        assertThat(response.getEstimatedSavingAmount()).isEqualByComparingTo("10000"); // 10%
    }
}
