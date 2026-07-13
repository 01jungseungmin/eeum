package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiOwnerMetricInputRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiElectricityReportResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiOperationRiskResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiSavingPlanResponseDto;
import com.eeum.eeum.application.ai.dto.response.DataSourceDto;
import com.eeum.eeum.application.ai.dto.response.EquipmentShareDto;
import com.eeum.eeum.application.ai.dto.response.GasSafetyInsightDto;
import com.eeum.eeum.application.ai.generator.TemplateAiInsightGenerator;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.entity.AiOwnerMetricInput;
import com.eeum.eeum.domain.ai.enums.AiDataSourceType;
import com.eeum.eeum.domain.ai.enums.AiMetricType;
import com.eeum.eeum.domain.ai.enums.AiRiskLevel;
import com.eeum.eeum.domain.ai.enums.AiSavingPlanStatus;
import com.eeum.eeum.domain.ai.repository.AiOwnerMetricInputRepository;
import com.eeum.eeum.domain.ai.repository.AiSavingPlanRepository;
import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.infrastructure.external.PublicDataService;
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
import java.util.Map;
import java.util.Optional;

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
    @Mock private com.eeum.eeum.infrastructure.external.PublicDataService publicDataService;
    @Mock private com.eeum.eeum.domain.external.repository.ExternalEnergyUsageStatRepository externalEnergyUsageStatRepository;
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
        // 기본값: 최근 리뷰/문의 없음 — 안전 키워드가 감지되지 않는 상태
        lenient().when(storeReviewRepository.findByStore_StoreIdAndCreatedAtAfter(eq(STORE_ID), any()))
                .thenReturn(List.of());
        lenient().when(inquiryRepository.findByStore_StoreIdAndCreatedAtAfter(eq(STORE_ID), any()))
                .thenReturn(List.of());
        return store;
    }

    private StoreReview reviewWithContent(String content) {
        StoreReview review = mock(StoreReview.class);
        lenient().when(review.getContent()).thenReturn(content);
        return review;
    }

    private Inquiry inquiryWithContent(String title, String content) {
        Inquiry inquiry = mock(Inquiry.class);
        lenient().when(inquiry.getTitle()).thenReturn(title);
        lenient().when(inquiry.getContent()).thenReturn(content);
        return inquiry;
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

    // ──────────────────── 가스 안전 인사이트 (KGS 공공데이터) ────────────────────

    @Test
    void KGS_주요원인과_가스설비_리뷰키워드가_결합되면_safetyCheck는_CAUTION이고_gasSafetyInsight에_반영된다() {
        // given
        Store store = stubStore();
        stubMetricInputs(metric(store, AiMetricType.GAS_EQUIPMENT_COUNT, new BigDecimal("1")));
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(false);
        PublicDataService.GasAccidentSummary summary =
                new PublicDataService.GasAccidentSummary(158, Map.of("사용자취급부주의", 48), "사용자취급부주의", 48, 30.4);
        List<StoreReview> reviews = List.of(reviewWithContent("음식에서 탄 냄새가 나서 놀랐어요"));
        List<Inquiry> inquiries = List.of(inquiryWithContent("문의", "주방에서 연기가 나는 것 같아요"));
        when(publicDataService.getGasAccidentSummary()).thenReturn(Optional.of(summary));
        when(storeReviewRepository.findByStore_StoreIdAndCreatedAtAfter(eq(STORE_ID), any())).thenReturn(reviews);
        when(inquiryRepository.findByStore_StoreIdAndCreatedAtAfter(eq(STORE_ID), any())).thenReturn(inquiries);

        // when
        AiOperationRiskResponseDto response = operationRiskService.getRisks(OWNER_ID);

        // then
        assertThat(response.getSafetyCheckLevel()).isEqualTo(AiRiskLevel.CAUTION);
        assertThat(response.getSafetyCheck()).contains("사용자취급부주의").contains("30.4").contains("가스 설비를 사용 중");

        GasSafetyInsightDto insight = response.getGasSafetyInsight();
        assertThat(insight.getPublicAccidentCount()).isEqualTo(158);
        assertThat(insight.getTopCause()).isEqualTo("사용자취급부주의");
        assertThat(insight.getTopCauseRatio()).isEqualTo(30.4);
        assertThat(insight.getGasEquipmentCount()).isEqualTo(1);
        assertThat(insight.getDetectedKeywords()).contains("냄새", "연기");
        assertThat(insight.getRecommendedActions()).containsExactly(
                "영업 전 가스 밸브 잠금 상태 확인", "조리 중 자리 비움 방지", "마감 전 중간밸브·메인밸브 재확인");
        assertThat(insight.getSourceType()).isEqualTo(AiDataSourceType.PUBLIC_DATA);
        assertThat(response.getDataSources())
                .extracting(DataSourceDto::getSourceType)
                .contains(AiDataSourceType.PUBLIC_DATA);
    }

    @Test
    void 안전_키워드가_3건_이상_감지되면_설비_보유_여부와_무관하게_WARNING이다() {
        // given
        Store store = stubStore();
        stubMetricInputs();
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(false);
        List<StoreReview> reviews = List.of(
                reviewWithContent("냄새가 심했어요"),
                reviewWithContent("매캐한 연기가 느껴졌어요"));
        List<Inquiry> inquiries = List.of(inquiryWithContent("불안해요", "화기 관리가 걱정됩니다"));
        when(storeReviewRepository.findByStore_StoreIdAndCreatedAtAfter(eq(STORE_ID), any())).thenReturn(reviews);
        when(inquiryRepository.findByStore_StoreIdAndCreatedAtAfter(eq(STORE_ID), any())).thenReturn(inquiries);

        // when
        AiOperationRiskResponseDto response = operationRiskService.getRisks(OWNER_ID);

        // then
        assertThat(response.getSafetyCheckLevel()).isEqualTo(AiRiskLevel.WARNING);
        assertThat(response.getGasSafetyInsight().getDetectedKeywords()).isNotEmpty();
    }

    @Test
    void 가스_냄새_누설_조합이_동시에_감지되면_매칭_건수와_무관하게_WARNING이다() {
        // given — 매칭된 텍스트는 1건뿐이지만 "가스"+"냄새"+"누설" 조합이 동시에 감지되면 즉시 WARNING
        Store store = stubStore();
        stubMetricInputs();
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(false);
        List<StoreReview> reviews = List.of(reviewWithContent("가스 냄새가 나고 누설이 의심돼요"));
        when(storeReviewRepository.findByStore_StoreIdAndCreatedAtAfter(eq(STORE_ID), any())).thenReturn(reviews);

        // when
        AiOperationRiskResponseDto response = operationRiskService.getRisks(OWNER_ID);

        // then
        assertThat(response.getSafetyCheckLevel()).isEqualTo(AiRiskLevel.WARNING);
    }

    @Test
    void recommendedActions는_topCause에_따라_달라진다() {
        // given
        Store store = stubStore();
        stubMetricInputs();
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(false);

        // when & then — 시설미비
        when(publicDataService.getGasAccidentSummary()).thenReturn(Optional.of(
                new PublicDataService.GasAccidentSummary(10, Map.of("시설미비", 10), "시설미비", 10, 100.0)));
        assertThat(operationRiskService.getRisks(OWNER_ID).getGasSafetyInsight().getRecommendedActions())
                .containsExactly("가스 호스 균열·꺾임 여부 확인", "주방 환기팬 작동 상태 확인", "노후 배관·연결부 누설 여부 점검");

        // when & then — 공급자취급부주의
        when(publicDataService.getGasAccidentSummary()).thenReturn(Optional.of(
                new PublicDataService.GasAccidentSummary(10, Map.of("공급자취급부주의", 10), "공급자취급부주의", 10, 100.0)));
        assertThat(operationRiskService.getRisks(OWNER_ID).getGasSafetyInsight().getRecommendedActions())
                .containsExactly("가스 공급 설비 연결부 이상 여부 확인", "정기 점검 이력 확인", "이상 냄새 발생 시 공급 업체 문의");

        // when & then — 타공사
        when(publicDataService.getGasAccidentSummary()).thenReturn(Optional.of(
                new PublicDataService.GasAccidentSummary(10, Map.of("타공사", 10), "타공사", 10, 100.0)));
        assertThat(operationRiskService.getRisks(OWNER_ID).getGasSafetyInsight().getRecommendedActions())
                .containsExactly("매장 주변 공사 일정 확인", "가스 배관 인접 작업 여부 확인", "공사 후 가스 냄새·누설 여부 점검");

        // when & then — 제품노후(고장)
        when(publicDataService.getGasAccidentSummary()).thenReturn(Optional.of(
                new PublicDataService.GasAccidentSummary(10, Map.of("제품노후(고장)", 10), "제품노후(고장)", 10, 100.0)));
        assertThat(operationRiskService.getRisks(OWNER_ID).getGasSafetyInsight().getRecommendedActions())
                .containsExactly("노후 가스기기 교체 시점 확인", "버너 점화 상태 확인", "정기 점검 예약");
    }

    @Test
    void 가스설비도_없고_안전_키워드도_없으면_safetyCheckLevel은_NORMAL이고_원인은_기본_체크리스트다() {
        // given
        stubStore();
        stubMetricInputs();
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(false);
        when(publicDataService.getGasAccidentSummary()).thenReturn(Optional.empty());

        // when
        AiOperationRiskResponseDto response = operationRiskService.getRisks(OWNER_ID);

        // then
        assertThat(response.getSafetyCheckLevel()).isEqualTo(AiRiskLevel.NORMAL);
        GasSafetyInsightDto insight = response.getGasSafetyInsight();
        assertThat(insight.getPublicAccidentCount()).isNull();
        assertThat(insight.getDetectedKeywords()).isEmpty();
        assertThat(insight.getRecommendedActions()).containsExactly(
                "영업 전 가스 밸브·호스 점검", "주방 환기팬 작동·필터 상태 확인", "화기 주변 정리 및 소화기 점검");
        assertThat(insight.getSourceType()).isEqualTo(AiDataSourceType.LOCAL_AVERAGE_ONLY);
    }

    @Test
    void KGS_API_호출이_실패해도_전체_조회는_실패하지_않고_기본_문구로_대체된다() {
        // given
        Store store = stubStore();
        stubMetricInputs(metric(store, AiMetricType.GAS_EQUIPMENT_COUNT, new BigDecimal("1")));
        when(aiSavingPlanRepository.existsByStore_StoreIdAndStatus(STORE_ID, AiSavingPlanStatus.SAVED)).thenReturn(false);
        when(publicDataService.getGasAccidentSummary()).thenThrow(new RuntimeException("KGS API 호출 실패"));

        // when
        AiOperationRiskResponseDto response = operationRiskService.getRisks(OWNER_ID);

        // then — 예외가 전체 API 실패로 이어지지 않고 가스 설비 보유 기준 기본 문구로 대체된다
        assertThat(response.getSafetyCheck()).contains("가스 설비를 사용 중입니다");
        assertThat(response.getGasSafetyInsight().getPublicAccidentCount()).isNull();
        assertThat(response.getGasSafetyInsight().getSourceType()).isEqualTo(AiDataSourceType.LOCAL_AVERAGE_ONLY);
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
                .mapToInt(EquipmentShareDto::getRatio)
                .sum();
        assertThat(totalRatio).isEqualTo(100);
        assertThat(response.getEstimatedSavingAmount()).isEqualByComparingTo("10000"); // 10%
    }
}
