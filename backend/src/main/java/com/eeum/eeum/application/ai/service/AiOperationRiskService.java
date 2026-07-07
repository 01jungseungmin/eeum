package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiOwnerMetricInputRequestDto;
import com.eeum.eeum.application.ai.dto.request.AiSavingPlanSaveRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiElectricityReportResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiOperationRiskResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiSavingPlanResponseDto;
import com.eeum.eeum.application.ai.generator.AiInsightGenerator;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.domain.ai.entity.AiOwnerMetricInput;
import com.eeum.eeum.domain.ai.enums.AiDataSourceType;
import com.eeum.eeum.domain.ai.enums.AiMetricType;
import com.eeum.eeum.domain.ai.enums.AiRiskLevel;
import com.eeum.eeum.domain.ai.enums.AiSavingPlanStatus;
import com.eeum.eeum.domain.ai.repository.AiOwnerMetricInputRepository;
import com.eeum.eeum.domain.ai.repository.AiSavingPlanRepository;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.external.entity.ExternalEnergyUsageStat;
import com.eeum.eeum.domain.external.repository.ExternalEnergyUsageStatRepository;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.infrastructure.external.PublicDataService;
import com.eeum.eeum.infrastructure.external.dto.RegionEnergyUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

@Slf4j
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
    private final StoreReviewRepository storeReviewRepository;
    private final InquiryRepository inquiryRepository;
    private final AiOwnerMetricCommandExecutor ownerMetricCommandExecutor;
    private final AiSavingPlanCommandExecutor savingPlanCommandExecutor;
    private final PublicDataService publicDataService;
    private final ExternalEnergyUsageStatRepository externalEnergyUsageStatRepository;

    public AiOperationRiskResponseDto getRisks(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.OPERATION_RISK_SUMMARY);
        return buildRiskResponse(store, false);
    }

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

    // 절감 계획 저장 — 락 먼저 잡고 → Executor에서 @Transactional 시작 → TX 커밋 후 락 해제.
    // 동시 요청 시 액션 로그 중복 기록을 방지한다.
    public AiSavingPlanResponseDto saveSavingPlan(Long ownerId, AiSavingPlanSaveRequestDto request) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.SAVING_PLAN);
        Long savingPlanId = request != null ? request.getSavingPlanId() : null;
        return redisLockService.executeWithLock(LockKeys.aiSavingPlan(store.getStoreId()), SAVING_LOCK_LEASE,
                () -> savingPlanCommandExecutor.savePlanInTx(store, ownerId, savingPlanId));
    }

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

        // 2차: 업종/지역 비교 — OpenAPI 우선, 없으면 파일 Import 데이터, 둘 다 없으면 null
        IndustryComparison comparison = buildIndustryComparison(store);

        return AiElectricityReportResponseDto.builder()
                .monthlyUsages(monthlyUsages)
                .equipmentShares(List.of()) // 설비별 계측 데이터 미보유 — 3차 계측 연동 시 제공
                .keyDiagnosis(aiInsightGenerator.electricityDiagnosis(store.getName(), hasData))
                .analysisPeriod(recentMonths.get(0) + " ~ " + recentMonths.get(recentMonths.size() - 1))
                .industryComparison(comparison != null ? comparison.text() : null)
                .estimatedSavingAmount(percentOf(
                        findLatestMetricValue(storeId, AiMetricType.MONTHLY_POWER_BILL), 10))
                .sourceType(hasData ? AiDataSourceType.OWNER_INPUT
                        : comparison != null ? comparison.sourceType()
                        : AiDataSourceType.LOCAL_AVERAGE_ONLY)
                .hasData(hasData || comparison != null)
                .emptyMessage(hasData || comparison != null
                        ? null : "실측값이 없습니다. 월 전력 사용량을 입력하면 리포트가 생성됩니다.")
                .build();
    }

    private record IndustryComparison(String text, AiDataSourceType sourceType) {
    }

    // 업종/지역 전력 비교 — KEPCO OpenAPI → 파일 Import 데이터 순으로 조회, 전부 실패 시 null
    private IndustryComparison buildIndustryComparison(Store store) {
        String regionKeyword = resolveRegionKeyword(store);
        try {
            var usages = publicDataService.getIndustryPowerUsages(regionKeyword);
            if (!usages.isEmpty()) {
                var first = usages.get(0);
                return new IndustryComparison(String.format(
                        "공공데이터 기준 %s %s 평균 전력 사용량은 호당 %s kWh 수준입니다.",
                        first.region() != null ? first.region() : "우리 지역",
                        first.industry() != null ? first.industry() : "",
                        first.averageUsageKwh() != null
                                ? String.format("%,.0f", first.averageUsageKwh()) : "확인 불가"),
                        AiDataSourceType.PUBLIC_DATA);
            }
        } catch (Exception e) {
            log.warn("[AI-RISK] 업종 OpenAPI 조회 실패, 파일 데이터로 fallback: {}", e.getMessage());
        }
        try {
            List<ExternalEnergyUsageStat> stats = regionKeyword != null
                    ? externalEnergyUsageStatRepository.findBySigunguContainingOrderBySourcePeriodDesc(regionKeyword)
                    : externalEnergyUsageStatRepository.findTop12ByOrderBySourcePeriodDesc();
            if (!stats.isEmpty()) {
                ExternalEnergyUsageStat latest = stats.get(0);
                return new IndustryComparison(String.format(
                        "최근 공개된 파일 기준(%s) %s %s 전력 사용량은 %s kWh입니다. (적재일: %s)",
                        latest.getSourcePeriod(),
                        latest.getSigungu() != null ? latest.getSigungu() : "우리 지역",
                        latest.getUsageType() != null ? latest.getUsageType() : "",
                        latest.getUsageKwh() != null ? String.format("%,.0f", latest.getUsageKwh()) : "확인 불가",
                        latest.getImportedAt().toLocalDate()),
                        AiDataSourceType.FILE_IMPORTED_DATA);
            }
        } catch (Exception e) {
            log.warn("[AI-RISK] 파일 기반 에너지 통계 조회 실패: {}", e.getMessage());
        }
        return null;
    }

    // ===================== 내부 유틸 =====================

    private AiOperationRiskResponseDto buildRiskResponse(Store store, boolean detail) {
        Long storeId = store.getStoreId();
        boolean hasOwnerInput = aiOwnerMetricInputRepository.existsByStore_StoreId(storeId);
        AiRiskLevel level = resolveRiskLevel(storeId);
        boolean hasSavedPlan = aiSavingPlanRepository.existsByStore_StoreIdAndStatus(storeId, AiSavingPlanStatus.SAVED);

        AiDataSourceType sourceType = hasOwnerInput ? AiDataSourceType.OWNER_INPUT : AiDataSourceType.LOCAL_AVERAGE_ONLY;

        // 2차: 공공데이터 반영 (외부 장애 시 기존 문구로 fallback — 전체 API 실패로 이어지지 않음)
        String energySignal = buildEnergySignal(store, hasOwnerInput);
        String safetyCheck = buildSafetyCheck();
        List<AiOperationRiskResponseDto.DataSourceDto> dataSources = new java.util.ArrayList<>();
        dataSources.add(AiOperationRiskResponseDto.DataSourceDto.from(sourceType));
        if (usedPublicData(energySignal, safetyCheck)) {
            dataSources.add(AiOperationRiskResponseDto.DataSourceDto.from(AiDataSourceType.PUBLIC_DATA));
        }

        AiOperationRiskResponseDto.AiOperationRiskResponseDtoBuilder builder = AiOperationRiskResponseDto.builder()
                .overallRiskLevel(level)
                .energySignal(energySignal)
                .seasonalAlert(buildSeasonalAlert())
                .activityAnomaly(level == AiRiskLevel.NORMAL
                        ? "업종 활동에 특이 변화가 감지되지 않았습니다."
                        : "미답변 리뷰/문의 증가 신호가 있습니다.")
                .safetyCheck(safetyCheck)
                .aiJudgement(aiInsightGenerator.riskJudgement(store.getName(), level, hasOwnerInput))
                .dataSources(dataSources)
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

    // 동네 에너지 경기 신호 — KPX 행정구역별 에너지사용량(공공데이터)이 있으면 반영, 실패 시 기존 문구
    private String buildEnergySignal(Store store, boolean hasOwnerInput) {
        try {
            List<RegionEnergyUsage> usages = publicDataService.getRegionEnergyUsages(resolveRegionKeyword(store));
            if (!usages.isEmpty()) {
                RegionEnergyUsage latest = usages.get(0);
                return String.format("공공데이터 기준 %s %s 에너지 사용량은 %s 수준입니다. 지역 에너지 활동을 참고해 운영을 준비하세요.",
                        latest.region() != null ? latest.region() : "우리 지역",
                        latest.period() != null ? "(" + latest.period() + ")" : "",
                        formatUsage(latest.usage()));
            }
        } catch (Exception e) {
            log.warn("[AI-RISK] 지역 에너지 OpenAPI 조회 실패, 기존 문구로 fallback: {}", e.getMessage());
        }
        return hasOwnerInput
                ? "입력해주신 실측값 기준으로 분석 중입니다."
                : "실측값 미입력 — 지역 평균 기반 추정 신호입니다.";
    }

    // 안전 리스크 체크 — KGS 가스사고 현황(공공데이터)이 있으면 반영
    private String buildSafetyCheck() {
        try {
            return publicDataService.getGasAccidentSummary()
                    .map(summary -> summary.topCause() != null
                            ? String.format("최근 가스사고 통계 %d건 중 '%s' 원인이 가장 많습니다. 관련 설비를 우선 점검하세요.",
                            summary.totalCount(), summary.topCause())
                            : String.format("최근 가스사고 통계 %d건이 확인됩니다. 정기 안전 점검을 권장드려요.", summary.totalCount()))
                    .orElse("정기 안전 점검 체크리스트를 확인해주세요.");
        } catch (Exception e) {
            log.warn("[AI-RISK] 가스사고 통계 OpenAPI 조회 실패, 기본 문구 반환: {}", e.getMessage());
            return "정기 안전 점검 체크리스트를 확인해주세요.";
        }
    }

    private boolean usedPublicData(String energySignal, String safetyCheck) {
        return energySignal.startsWith("공공데이터") || safetyCheck.contains("가스사고 통계");
    }

    // 주소에서 시군구 키워드 추출 (예: "서울시 마포구 ..." → "마포구")
    private String resolveRegionKeyword(Store store) {
        if (store.getAddress() == null) {
            return null;
        }
        String[] tokens = store.getAddress().split("\\s+");
        for (String token : tokens) {
            if (token.endsWith("구") || token.endsWith("군") || (token.endsWith("시") && !token.equals(tokens[0]))) {
                return token;
            }
        }
        return tokens.length > 1 ? tokens[1] : null;
    }

    private String formatUsage(Double usage) {
        if (usage == null) {
            return "확인 불가";
        }
        return String.format("%,.0f", usage);
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
