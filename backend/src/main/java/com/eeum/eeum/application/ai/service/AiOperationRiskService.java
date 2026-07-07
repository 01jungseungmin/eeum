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
import com.eeum.eeum.domain.external.entity.ExternalEnergyUsageStat;
import com.eeum.eeum.domain.external.repository.ExternalEnergyUsageStatRepository;
import com.eeum.eeum.domain.store.entity.Store;
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
        AiOwnerMetricSnapshot snapshot = AiOwnerMetricSnapshot.load(aiOwnerMetricInputRepository, storeId);

        return AiElectricityReportResponseDto.builder()
                .monthlyUsages(monthlyUsages)
                .equipmentShares(buildEquipmentShares(snapshot))
                .keyDiagnosis(aiInsightGenerator.electricityDiagnosis(store.getName(), hasData))
                .analysisPeriod(recentMonths.get(0) + " ~ " + recentMonths.get(recentMonths.size() - 1))
                .industryComparison(comparison != null ? comparison.text() : null)
                .estimatedSavingAmount(percentOf(snapshot.monthlyPowerBill(), 10))
                .sourceType(hasData ? AiDataSourceType.OWNER_INPUT
                        : comparison != null ? comparison.sourceType()
                        : AiDataSourceType.LOCAL_AVERAGE_ONLY)
                .hasData(hasData || comparison != null)
                .emptyMessage(hasData || comparison != null
                        ? null : "실측값이 없습니다. 월 전력 사용량을 입력하면 리포트가 생성됩니다.")
                .build();
    }

    // 설비 보유 대수 기반 전력 사용 비중 추정 — 실측 계측이 아니므로 냉방/냉장 설비만 상대 비중으로 근사한다.
    // 설비 입력이 전혀 없으면 빈 리스트(실측 데이터 없음 — 기존 스키마 설명과 동일한 의미)
    private List<AiElectricityReportResponseDto.EquipmentShareDto> buildEquipmentShares(AiOwnerMetricSnapshot snapshot) {
        record WeightedEquipment(String name, BigDecimal count, int unitWeight) {
            boolean isPresent() {
                return count != null && count.compareTo(BigDecimal.ZERO) > 0;
            }

            BigDecimal weighted() {
                return count.multiply(BigDecimal.valueOf(unitWeight));
            }
        }

        List<WeightedEquipment> present = List.of(
                        new WeightedEquipment("냉방·공조", snapshot.airConditionerCount(), 3),
                        new WeightedEquipment("냉장·냉동", snapshot.refrigeratorCount(), 2))
                .stream()
                .filter(WeightedEquipment::isPresent)
                .toList();
        if (present.isEmpty()) {
            return List.of();
        }

        BigDecimal totalWeighted = present.stream()
                .map(WeightedEquipment::weighted)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return present.stream()
                .map(equipment -> AiElectricityReportResponseDto.EquipmentShareDto.builder()
                        .name(equipment.name())
                        .ratio(equipment.weighted()
                                .multiply(BigDecimal.valueOf(100))
                                .divide(totalWeighted, 0, RoundingMode.HALF_UP)
                                .intValue())
                        .build())
                .toList();
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
        AiOwnerMetricSnapshot snapshot = AiOwnerMetricSnapshot.load(aiOwnerMetricInputRepository, storeId);
        // 실측 기반 여부는 존재 여부(existsByStore_StoreId)가 아니라 실제로 위험 판단에 쓰이는
        // MONTHLY_POWER_KWH/MONTHLY_POWER_BILL 보유 여부로 판단한다 — 다른 지표만 입력된 경우를
        // "실측 기반"으로 잘못 표시하지 않기 위함.
        boolean hasPreciseData = snapshot.hasPreciseData();
        boolean hasSavedPlan = aiSavingPlanRepository.existsByStore_StoreIdAndStatus(storeId, AiSavingPlanStatus.SAVED);

        AiDataSourceType sourceType = hasPreciseData ? AiDataSourceType.OWNER_INPUT : AiDataSourceType.LOCAL_AVERAGE_ONLY;

        // 카드 4개를 각각 독립적으로 판단 — 위험도는 카드별로 다르게 나올 수 있다
        AiRiskLevel energySignalLevel = resolveEnergySignalLevel(snapshot);
        AiRiskLevel seasonalAlertLevel = resolveSeasonalAlertLevel(snapshot);
        AiRiskLevel activityAnomalyLevel = resolveActivityAnomalyLevel(snapshot);
        AiRiskLevel safetyCheckLevel = resolveSafetyCheckLevel(snapshot);
        AiRiskLevel overallRiskLevel = maxLevel(
                energySignalLevel, seasonalAlertLevel, activityAnomalyLevel, safetyCheckLevel);

        // 2차: 공공데이터 반영 (외부 장애 시 기존 문구로 fallback — 전체 API 실패로 이어지지 않음)
        String energySignal = buildEnergySignalText(store, snapshot);
        String safetyCheck = buildSafetyCheckText(snapshot);
        List<AiOperationRiskResponseDto.DataSourceDto> dataSources = new java.util.ArrayList<>();
        dataSources.add(AiOperationRiskResponseDto.DataSourceDto.from(sourceType));
        if (usedPublicData(energySignal, safetyCheck)) {
            dataSources.add(AiOperationRiskResponseDto.DataSourceDto.from(AiDataSourceType.PUBLIC_DATA));
        }

        AiOperationRiskResponseDto.AiOperationRiskResponseDtoBuilder builder = AiOperationRiskResponseDto.builder()
                .overallRiskLevel(overallRiskLevel)
                .energySignal(energySignal)
                .energySignalLevel(energySignalLevel)
                .seasonalAlert(buildSeasonalAlertText(snapshot))
                .seasonalAlertLevel(seasonalAlertLevel)
                .activityAnomaly(buildActivityAnomalyText(activityAnomalyLevel, snapshot))
                .activityAnomalyLevel(activityAnomalyLevel)
                .safetyCheck(safetyCheck)
                .safetyCheckLevel(safetyCheckLevel)
                .aiJudgement(aiInsightGenerator.riskJudgement(store.getName(), overallRiskLevel, hasPreciseData))
                .dataSources(dataSources)
                .sourceType(sourceType)
                .hasPreciseData(hasPreciseData)
                .notice(hasPreciseData
                        ? "사장님이 입력한 실측값 기반 분석입니다."
                        : "실시간 측정값이 아닌 추정 분석입니다.")
                .hasSavedPlan(hasSavedPlan);

        if (detail) {
            builder.checklist(aiInsightGenerator.safetyChecklist(store.getName()));
        }
        return builder.build();
    }

    // 동네 에너지 경기 신호 — 월 전력 사용량(kWh) 기준
    private AiRiskLevel resolveEnergySignalLevel(AiOwnerMetricSnapshot snapshot) {
        BigDecimal kwh = snapshot.monthlyPowerKwh();
        if (kwh == null) {
            return AiRiskLevel.NORMAL;
        }
        if (kwh.compareTo(BigDecimal.valueOf(800)) >= 0) {
            return AiRiskLevel.WARNING;
        }
        if (kwh.compareTo(BigDecimal.valueOf(700)) >= 0) {
            return AiRiskLevel.CAUTION;
        }
        return AiRiskLevel.NORMAL;
    }

    // 계절/시기 선제 알림 — 성수기(여름/겨울)에는 기본 CAUTION, 사용량까지 높으면 WARNING
    private AiRiskLevel resolveSeasonalAlertLevel(AiOwnerMetricSnapshot snapshot) {
        if (!isPeakSeason()) {
            return AiRiskLevel.NORMAL;
        }
        BigDecimal kwh = snapshot.monthlyPowerKwh();
        if (kwh != null && kwh.compareTo(BigDecimal.valueOf(750)) >= 0) {
            return AiRiskLevel.WARNING;
        }
        return AiRiskLevel.CAUTION;
    }

    // 업종 활동 이상 변화 감지 — 동네 평균 대역(580~720kWh)을 벗어나면 CAUTION
    private AiRiskLevel resolveActivityAnomalyLevel(AiOwnerMetricSnapshot snapshot) {
        BigDecimal kwh = snapshot.monthlyPowerKwh();
        if (kwh == null) {
            return AiRiskLevel.NORMAL;
        }
        boolean withinNormalBand = kwh.compareTo(BigDecimal.valueOf(580)) >= 0
                && kwh.compareTo(BigDecimal.valueOf(720)) <= 0;
        return withinNormalBand ? AiRiskLevel.NORMAL : AiRiskLevel.CAUTION;
    }

    // 안전 리스크 체크 — 가스 설비를 사용 중이면 CAUTION
    private AiRiskLevel resolveSafetyCheckLevel(AiOwnerMetricSnapshot snapshot) {
        return snapshot.hasGasEquipment() ? AiRiskLevel.CAUTION : AiRiskLevel.NORMAL;
    }

    private AiRiskLevel maxLevel(AiRiskLevel... levels) {
        AiRiskLevel max = AiRiskLevel.NORMAL;
        for (AiRiskLevel level : levels) {
            if (level.ordinal() > max.ordinal()) {
                max = level;
            }
        }
        return max;
    }

    private boolean isPeakSeason() {
        int month = LocalDateTime.now().getMonthValue();
        return (month >= 6 && month <= 8) || month == 12 || month <= 2;
    }

    // 동네 에너지 경기 신호 — KPX 행정구역별 에너지사용량(공공데이터)이 있으면 반영, 실패 시 기존 문구
    private String buildEnergySignalText(Store store, AiOwnerMetricSnapshot snapshot) {
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
        return snapshot.monthlyPowerKwh() != null
                ? "입력해주신 실측값 기준으로 분석 중입니다."
                : "실측값 미입력 — 지역 평균 기반 추정 신호입니다.";
    }

    // 안전 리스크 체크 — KGS 가스사고 현황(공공데이터)이 있으면 반영, 없으면 가스 설비 보유 여부에 맞춘 기본 문구
    private String buildSafetyCheckText(AiOwnerMetricSnapshot snapshot) {
        String fallback = snapshot.hasGasEquipment()
                ? "가스 설비를 사용 중입니다. 밸브·호스 상태를 정기적으로 점검해주세요."
                : "정기 안전 점검 체크리스트를 확인해주세요.";
        try {
            return publicDataService.getGasAccidentSummary()
                    .map(summary -> summary.topCause() != null
                            ? String.format("최근 가스사고 통계 %d건 중 '%s' 원인이 가장 많습니다. 관련 설비를 우선 점검하세요.",
                            summary.totalCount(), summary.topCause())
                            : String.format("최근 가스사고 통계 %d건이 확인됩니다. 정기 안전 점검을 권장드려요.", summary.totalCount()))
                    .orElse(fallback);
        } catch (Exception e) {
            log.warn("[AI-RISK] 가스사고 통계 OpenAPI 조회 실패, 기본 문구 반환: {}", e.getMessage());
            return fallback;
        }
    }

    private String buildActivityAnomalyText(AiRiskLevel level, AiOwnerMetricSnapshot snapshot) {
        if (snapshot.monthlyPowerKwh() == null) {
            return "전력 사용량 실측값이 없어 동네 평균 기준으로만 판단하고 있습니다.";
        }
        return level == AiRiskLevel.NORMAL
                ? "업종 활동에 특이 변화가 감지되지 않았습니다."
                : "평소 대비 전력 사용 패턴에 변화가 감지됐습니다. 운영 상황을 확인해보세요.";
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

    // 여름철(6~8월)에는 냉방 설비 보유 시 점검 문구를 덧붙이고, 겨울철(12~2월)에는 난방/동파 문구를 안내한다
    private String buildSeasonalAlertText(AiOwnerMetricSnapshot snapshot) {
        int month = LocalDateTime.now().getMonthValue();
        if (month >= 6 && month <= 8) {
            String base = "여름철 냉방·냉장 부하가 커지는 시기입니다. 전기 사용량을 미리 점검하세요.";
            if (snapshot.hasAirConditioner()) {
                base += " 보유하신 냉방 설비 점검을 성수기 전에 마쳐두시길 권장드려요.";
            }
            return base;
        }
        if (month == 12 || month <= 2) {
            return "겨울철 난방·동파 위험이 커지는 시기입니다. 배관과 난방 설비를 점검하세요.";
        }
        return "계절 특이 위험 신호는 없습니다.";
    }

    private BigDecimal percentOf(BigDecimal base, int percent) {
        if (base == null) {
            return null;
        }
        return base.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }
}
