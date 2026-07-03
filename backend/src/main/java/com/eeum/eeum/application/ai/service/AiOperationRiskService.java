package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiOwnerMetricInputRequestDto;
import com.eeum.eeum.application.ai.dto.request.AiSavingPlanSaveRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiElectricityReportResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiOperationRiskResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiSavingPlanResponseDto;
import com.eeum.eeum.application.ai.generator.AiInsightGenerator;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.domain.ai.entity.AiActionLog;
import com.eeum.eeum.domain.ai.entity.AiOwnerMetricInput;
import com.eeum.eeum.domain.ai.entity.AiSavingPlan;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import com.eeum.eeum.domain.ai.enums.AiDataSourceType;
import com.eeum.eeum.domain.ai.enums.AiMetricType;
import com.eeum.eeum.domain.ai.enums.AiRiskLevel;
import com.eeum.eeum.domain.ai.enums.AiSavingPlanStatus;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiOwnerMetricInputRepository;
import com.eeum.eeum.domain.ai.repository.AiSavingPlanRepository;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class AiOperationRiskService {

    private static final Duration METRIC_LOCK_LEASE = Duration.ofSeconds(5);
    private static final Duration SAVING_LOCK_LEASE = Duration.ofSeconds(5);

    private final AiManagerSupportService supportService;
    private final RedisLockService redisLockService;
    private final AiInsightGenerator aiInsightGenerator;
    private final AiOwnerMetricInputRepository aiOwnerMetricInputRepository;
    private final AiSavingPlanRepository aiSavingPlanRepository;
    private final AiActionLogRepository aiActionLogRepository;
    private final StoreReviewRepository storeReviewRepository;
    private final InquiryRepository inquiryRepository;
    private final AiOwnerMetricCommandExecutor ownerMetricCommandExecutor;
    private final AiSavingPlanCommandExecutor savingPlanCommandExecutor;

    @Transactional(readOnly = true)
    public AiOperationRiskResponseDto getRisks(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.OPERATION_RISK_SUMMARY);
        return buildRiskResponse(store, false);
    }

    @Transactional(readOnly = true)
    public AiOperationRiskResponseDto getRiskDetail(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.OPERATION_RISK_DETAIL);
        return buildRiskResponse(store, true);
    }

    // 사장님 실측값 입력 — 락 먼저 잡고 → Executor에서 @Transactional 시작 → TX 커밋 후 락 해제
    // upsert + action log 를 단일 TX로 묶어 커밋 전 락 해제로 인한 중복 insert 방지
    public void saveOwnerInput(Long ownerId, AiOwnerMetricInputRequestDto request) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.OPERATION_RISK_SUMMARY);

        redisLockService.executeWithLock(
                LockKeys.aiOwnerMetric(store.getStoreId(), request.getMetricType(), request.getYearMonth()),
                METRIC_LOCK_LEASE,
                () -> ownerMetricCommandExecutor.upsertMetricInTx(store, ownerId, request));
    }

    // 절감 계획 생성 — 락 먼저 잡고 → Executor에서 @Transactional 시작 → TX 커밋 후 락 해제.
    // check-then-insert를 락으로 직렬화해 동시 요청으로 인한 DRAFT 중복 생성 방지.
    public AiSavingPlanResponseDto createSavingPlan(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.SAVING_PLAN);
        return redisLockService.executeWithLock(LockKeys.aiSavingPlan(store.getStoreId()), SAVING_LOCK_LEASE,
                () -> savingPlanCommandExecutor.createSavingPlanInTx(store));
    }

    @Transactional
    public AiSavingPlanResponseDto saveSavingPlan(Long ownerId, AiSavingPlanSaveRequestDto request) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.SAVING_PLAN);

        AiSavingPlan plan = (request != null && request.getSavingPlanId() != null
                ? aiSavingPlanRepository.findByAiSavingPlanIdAndStore_StoreId(request.getSavingPlanId(), store.getStoreId())
                : aiSavingPlanRepository.findFirstByStore_StoreIdOrderByCreatedAtDesc(store.getStoreId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_SAVING_PLAN_NOT_FOUND));

        plan.save();
        aiActionLogRepository.save(AiActionLog.record(
                store, store.getAccount(), AiActionType.SAVING_PLAN_SAVED,
                "AI_SAVING_PLAN", plan.getAiSavingPlanId(), "절감 계획 저장"));
        return AiSavingPlanResponseDto.from(plan, "이번 주부터 순차 실행 권장");
    }

    @Transactional(readOnly = true)
    public AiElectricityReportResponseDto getElectricityReport(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.ELECTRICITY_REPORT);
        Long storeId = store.getStoreId();

        List<String> recentMonths = IntStream.rangeClosed(0, 5)
                .mapToObj(i -> YearMonth.now().minusMonths(5 - i).toString())
                .toList();
        Map<String, AiOwnerMetricInput> inputsByMonth = aiOwnerMetricInputRepository
                .findByStore_StoreIdAndMetricTypeAndYearMonthInOrderByYearMonthAsc(
                        storeId, AiMetricType.MONTHLY_POWER_KWH, recentMonths)
                .stream()
                .collect(Collectors.toMap(AiOwnerMetricInput::getYearMonth, Function.identity()));

        boolean hasData = !inputsByMonth.isEmpty();
        List<AiElectricityReportResponseDto.MonthlyUsageDto> monthlyUsages = recentMonths.stream()
                .map(month -> AiElectricityReportResponseDto.MonthlyUsageDto.builder()
                        .yearMonth(month)
                        .kwh(inputsByMonth.containsKey(month) ? inputsByMonth.get(month).getValue() : null)
                        .build())
                .toList();

        return AiElectricityReportResponseDto.builder()
                .monthlyUsages(monthlyUsages)
                .equipmentShares(List.of()) // 설비별 계측 데이터 미보유 — 2차 공공데이터/계측 연동 시 제공
                .keyDiagnosis(aiInsightGenerator.electricityDiagnosis(store.getName(), hasData))
                .analysisPeriod(recentMonths.get(0) + " ~ " + recentMonths.get(recentMonths.size() - 1))
                .industryComparison(null) // 공공데이터 미연동 — 2차에서 제공
                .estimatedSavingAmount(percentOf(
                        findLatestMetricValue(storeId, AiMetricType.MONTHLY_POWER_BILL), 10))
                .sourceType(hasData ? AiDataSourceType.OWNER_INPUT : AiDataSourceType.LOCAL_AVERAGE_ONLY)
                .hasData(hasData)
                .emptyMessage(hasData ? null : "실측값이 없습니다. 월 전력 사용량을 입력하면 리포트가 생성됩니다.")
                .build();
    }

    // ===================== 내부 유틸 =====================

    private AiOperationRiskResponseDto buildRiskResponse(Store store, boolean detail) {
        Long storeId = store.getStoreId();
        boolean hasOwnerInput = aiOwnerMetricInputRepository.existsByStore_StoreId(storeId);
        AiRiskLevel level = resolveRiskLevel(storeId);
        boolean hasSavedPlan = aiSavingPlanRepository.existsByStore_StoreIdAndStatus(storeId, AiSavingPlanStatus.SAVED);

        AiDataSourceType sourceType = hasOwnerInput ? AiDataSourceType.OWNER_INPUT : AiDataSourceType.LOCAL_AVERAGE_ONLY;

        AiOperationRiskResponseDto.AiOperationRiskResponseDtoBuilder builder = AiOperationRiskResponseDto.builder()
                .overallRiskLevel(level)
                .energySignal(hasOwnerInput
                        ? "입력해주신 실측값 기준으로 분석 중입니다."
                        : "실측값 미입력 — 지역 평균 기반 추정 신호입니다.")
                .seasonalAlert(buildSeasonalAlert())
                .activityAnomaly(level == AiRiskLevel.NORMAL
                        ? "업종 활동에 특이 변화가 감지되지 않았습니다."
                        : "미답변 리뷰/문의 증가 신호가 있습니다.")
                .safetyCheck("정기 안전 점검 체크리스트를 확인해주세요.")
                .aiJudgement(aiInsightGenerator.riskJudgement(store.getName(), level, hasOwnerInput))
                .dataSources(List.of(AiOperationRiskResponseDto.DataSourceDto.from(sourceType)))
                .sourceType(sourceType)
                .hasPreciseData(hasOwnerInput)
                .notice(hasOwnerInput
                        ? "사장님이 입력한 실측값 기반 분석입니다."
                        : "실시간 측정값이 아닌 추정 분석입니다.")
                .hasSavedPlan(hasSavedPlan);

        if (detail) {
            builder.checklist(aiInsightGenerator.safetyChecklist(store.getName()));
        }
        return builder.build();
    }

    // 최근 2주 저평점 리뷰 + 미답변 문의 수 기반 위험 신호 판정
    private AiRiskLevel resolveRiskLevel(Long storeId) {
        List<StoreReview> recentReviews = storeReviewRepository
                .findByStore_StoreIdAndCreatedAtAfter(storeId, LocalDateTime.now().minusWeeks(2));
        long lowRatingCount = recentReviews.stream().filter(review -> review.getRating() <= 2).count();
        long pendingInquiries = inquiryRepository.countByStore_StoreIdAndStatus(storeId, InquiryStatus.PENDING);

        if (lowRatingCount >= 3) {
            return AiRiskLevel.WARNING;
        }
        if (lowRatingCount >= 1 || pendingInquiries >= 3) {
            return AiRiskLevel.CAUTION;
        }
        return AiRiskLevel.NORMAL;
    }

    private String buildSeasonalAlert() {
        int month = LocalDateTime.now().getMonthValue();
        if (month >= 6 && month <= 8) {
            return "여름철 냉방·냉장 부하가 커지는 시기입니다. 전기 사용량을 미리 점검하세요.";
        }
        if (month == 12 || month <= 2) {
            return "겨울철 난방·동파 위험이 커지는 시기입니다. 배관과 난방 설비를 점검하세요.";
        }
        return "계절 특이 위험 신호는 없습니다.";
    }

    private BigDecimal findLatestMetricValue(Long storeId, AiMetricType metricType) {
        return aiOwnerMetricInputRepository
                .findByStore_StoreIdAndMetricTypeAndYearMonth(storeId, metricType, YearMonth.now().toString())
                .or(() -> aiOwnerMetricInputRepository.findByStore_StoreIdAndMetricTypeAndYearMonth(
                        storeId, metricType, YearMonth.now().minusMonths(1).toString()))
                .map(AiOwnerMetricInput::getValue)
                .orElse(null);
    }

    private BigDecimal percentOf(BigDecimal base, int percent) {
        if (base == null) {
            return null;
        }
        return base.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }
}
