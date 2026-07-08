package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiOwnerMetricInputRequestDto;
import com.eeum.eeum.application.ai.dto.request.AiSavingPlanSaveRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiElectricityReportResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiOperationRiskResponseDto;
import com.eeum.eeum.application.ai.dto.response.AiSavingPlanResponseDto;
import com.eeum.eeum.application.ai.dto.response.DataSourceDto;
import com.eeum.eeum.application.ai.dto.response.EquipmentShareDto;
import com.eeum.eeum.application.ai.dto.response.GasSafetyInsightDto;
import com.eeum.eeum.application.ai.dto.response.MonthlyUsageDto;
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
import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreReview;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiOperationRiskService {

    private static final Duration METRIC_LOCK_LEASE = Duration.ofSeconds(5);
    private static final Duration SAVING_LOCK_LEASE = Duration.ofSeconds(5);

    // 리뷰/문의에서 가스 안전 관련 신호를 잡아내기 위한 키워드 — 최근 2주 텍스트 대상으로 검사
    private static final Duration SAFETY_KEYWORD_LOOKBACK = Duration.ofDays(14);
    private static final List<String> SAFETY_KEYWORDS =
            List.of("냄새", "연기", "가스", "탄 냄새", "환기", "매캐", "불", "화기", "누설", "폭발");
    // 이 조합이 동시에 감지되면 매칭 건수와 무관하게 즉시 WARNING (누출 의심 신호)
    private static final List<String> SEVERE_KEYWORD_COMBO = List.of("가스", "냄새", "누설");

    // 주요 사고 원인별 맞춤 대응 체크리스트 (제14회 산업통상부 공공데이터 공모전 — KGS 가스사고 현황 활용)
    private static final List<String> DEFAULT_SAFETY_ACTIONS = List.of(
            "영업 전 가스 밸브·호스 점검",
            "주방 환기팬 작동·필터 상태 확인",
            "화기 주변 정리 및 소화기 점검");
    private static final List<String> USER_MISHANDLING_ACTIONS = List.of(
            "영업 전 가스 밸브 잠금 상태 확인",
            "조리 중 자리 비움 방지",
            "마감 전 중간밸브·메인밸브 재확인");
    private static final List<String> FACILITY_DEFICIENCY_ACTIONS = List.of(
            "가스 호스 균열·꺾임 여부 확인",
            "주방 환기팬 작동 상태 확인",
            "노후 배관·연결부 누설 여부 점검");
    private static final List<String> AGING_EQUIPMENT_ACTIONS = List.of(
            "노후 가스기기 교체 시점 확인",
            "버너 점화 상태 확인",
            "정기 점검 예약");
    private static final List<String> SUPPLIER_MISHANDLING_ACTIONS = List.of(
            "가스 공급 설비 연결부 이상 여부 확인",
            "정기 점검 이력 확인",
            "이상 냄새 발생 시 공급 업체 문의");
    private static final List<String> THIRD_PARTY_CONSTRUCTION_ACTIONS = List.of(
            "매장 주변 공사 일정 확인",
            "가스 배관 인접 작업 여부 확인",
            "공사 후 가스 냄새·누설 여부 점검");

    private final AiManagerSupportService supportService;
    private final RedisLockService redisLockService;
    private final AiInsightGenerator aiInsightGenerator;
    private final AiOwnerMetricInputRepository aiOwnerMetricInputRepository;
    private final AiSavingPlanRepository aiSavingPlanRepository;
    private final AiOwnerMetricCommandExecutor ownerMetricCommandExecutor;
    private final AiSavingPlanCommandExecutor savingPlanCommandExecutor;
    private final PublicDataService publicDataService;
    private final ExternalEnergyUsageStatRepository externalEnergyUsageStatRepository;
    private final StoreReviewRepository storeReviewRepository;
    private final InquiryRepository inquiryRepository;

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
        List<MonthlyUsageDto> monthlyUsages = recentMonths.stream()
                .map(month -> MonthlyUsageDto.builder()
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
    private List<EquipmentShareDto> buildEquipmentShares(AiOwnerMetricSnapshot snapshot) {
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
                .map(equipment -> EquipmentShareDto.builder()
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

        // 안전 리스크 체크에 쓰일 공공데이터(KGS 가스사고)와 리뷰/문의 키워드 신호를 한 번만 계산해 재사용
        Optional<PublicDataService.GasAccidentSummary> gasSummary = fetchGasAccidentSummary();
        SafetyKeywordSignal keywordSignal = detectSafetyKeywords(storeId);

        // 카드 4개를 각각 독립적으로 판단 — 위험도는 카드별로 다르게 나올 수 있다
        AiRiskLevel energySignalLevel = resolveEnergySignalLevel(snapshot);
        AiRiskLevel seasonalAlertLevel = resolveSeasonalAlertLevel(snapshot);
        AiRiskLevel activityAnomalyLevel = resolveActivityAnomalyLevel(snapshot);
        AiRiskLevel safetyCheckLevel = resolveSafetyCheckLevel(snapshot, keywordSignal);
        AiRiskLevel overallRiskLevel = maxLevel(
                energySignalLevel, seasonalAlertLevel, activityAnomalyLevel, safetyCheckLevel);

        // 2차: 공공데이터 반영 (외부 장애 시 기존 문구로 fallback — 전체 API 실패로 이어지지 않음)
        String energySignal = buildEnergySignalText(store, snapshot);
        String safetyCheck = buildSafetyCheckText(snapshot, gasSummary, keywordSignal);
        List<DataSourceDto> dataSources = new java.util.ArrayList<>();
        dataSources.add(DataSourceDto.from(sourceType));
        if (usedPublicData(energySignal, gasSummary)) {
            dataSources.add(DataSourceDto.from(AiDataSourceType.PUBLIC_DATA));
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
                .gasSafetyInsight(buildGasSafetyInsight(snapshot, gasSummary, keywordSignal))
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

    // 안전 리스크 체크 — 가스 설비 보유 또는 리뷰/문의 키워드 감지 시 CAUTION, 키워드 3건 이상이면 WARNING
    private AiRiskLevel resolveSafetyCheckLevel(AiOwnerMetricSnapshot snapshot, SafetyKeywordSignal keywordSignal) {
        AiRiskLevel level = snapshot.hasGasEquipment() ? AiRiskLevel.CAUTION : AiRiskLevel.NORMAL;
        if (!keywordSignal.detectedKeywords().isEmpty()) {
            level = maxLevel(level, AiRiskLevel.CAUTION);
        }
        // 키워드가 반복(3건 이상)되거나 "가스+냄새+누설" 조합이 동시에 감지되면 누출 의심 — 즉시 WARNING
        boolean severeCombo = keywordSignal.detectedKeywords().containsAll(SEVERE_KEYWORD_COMBO);
        if (keywordSignal.matchedTextCount() >= 3 || severeCombo) {
            level = AiRiskLevel.WARNING;
        }
        return level;
    }

    // KGS 가스사고 현황 — 하위 레이어(PublicDataApiClient)가 이미 실패를 빈 리스트로 흡수하지만,
    // 방어적으로 한 번 더 감싸 예외가 전체 API 실패로 번지지 않게 한다.
    private Optional<PublicDataService.GasAccidentSummary> fetchGasAccidentSummary() {
        try {
            return publicDataService.getGasAccidentSummary();
        } catch (Exception e) {
            log.warn("[AI-RISK] 가스사고 통계 조회 실패, 기본 문구로 fallback: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // 최근 2주 리뷰/문의 텍스트에서 안전 관련 키워드를 감지 — 매칭된 "건수"(텍스트 단위) 기준으로 신호 강도를 판단
    private SafetyKeywordSignal detectSafetyKeywords(Long storeId) {
        LocalDateTime since = LocalDateTime.now().minus(SAFETY_KEYWORD_LOOKBACK);

        List<String> texts = new java.util.ArrayList<>();
        for (StoreReview review : storeReviewRepository.findByStore_StoreIdAndCreatedAtAfter(storeId, since)) {
            texts.add(review.getContent());
        }
        for (Inquiry inquiry : inquiryRepository.findByStore_StoreIdAndCreatedAtAfter(storeId, since)) {
            texts.add((inquiry.getTitle() != null ? inquiry.getTitle() : "")
                    + " " + (inquiry.getContent() != null ? inquiry.getContent() : ""));
        }

        Set<String> detectedKeywords = new LinkedHashSet<>();
        int matchedTextCount = 0;
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            boolean matched = false;
            for (String keyword : SAFETY_KEYWORDS) {
                if (text.contains(keyword)) {
                    detectedKeywords.add(keyword);
                    matched = true;
                }
            }
            if (matched) {
                matchedTextCount++;
            }
        }
        return new SafetyKeywordSignal(List.copyOf(detectedKeywords), matchedTextCount);
    }

    // 리뷰/문의 감지 결과 — detectedKeywords: 감지된 키워드 종류, matchedTextCount: 키워드가 감지된 리뷰/문의 "건수"
    private record SafetyKeywordSignal(List<String> detectedKeywords, int matchedTextCount) {
    }

    // 원인별 맞춤 대응 체크리스트 — 매칭되는 원인이 없으면 기본 체크리스트
    private List<String> recommendedActionsFor(String topCause) {
        if (topCause == null) {
            return DEFAULT_SAFETY_ACTIONS;
        }
        if (topCause.contains("사용자") && topCause.contains("취급")) {
            return USER_MISHANDLING_ACTIONS;
        }
        if (topCause.contains("공급자") && topCause.contains("취급")) {
            return SUPPLIER_MISHANDLING_ACTIONS;
        }
        if (topCause.contains("시설미비")) {
            return FACILITY_DEFICIENCY_ACTIONS;
        }
        if (topCause.contains("노후")) {
            return AGING_EQUIPMENT_ACTIONS;
        }
        if (topCause.contains("타공사")) {
            return THIRD_PARTY_CONSTRUCTION_ACTIONS;
        }
        return DEFAULT_SAFETY_ACTIONS;
    }

    // 가스 안전 인사이트 — KGS 공공데이터 + 사장님 입력값(GAS_EQUIPMENT_COUNT) + 리뷰/문의 키워드를 하나로 결합
    private GasSafetyInsightDto buildGasSafetyInsight(
            AiOwnerMetricSnapshot snapshot,
            Optional<PublicDataService.GasAccidentSummary> gasSummary,
            SafetyKeywordSignal keywordSignal
    ) {
        String topCause = gasSummary.map(PublicDataService.GasAccidentSummary::topCause).orElse(null);
        return GasSafetyInsightDto.builder()
                .publicAccidentCount(gasSummary.map(PublicDataService.GasAccidentSummary::totalCount).orElse(null))
                .topCause(topCause)
                .topCauseRatio(gasSummary.map(PublicDataService.GasAccidentSummary::topCauseRatio).orElse(null))
                .gasEquipmentCount(snapshot.gasEquipmentCount() != null ? snapshot.gasEquipmentCount().intValue() : null)
                .detectedKeywords(keywordSignal.detectedKeywords())
                .recommendedActions(recommendedActionsFor(topCause))
                .sourceType(gasSummary.isPresent() ? AiDataSourceType.PUBLIC_DATA : AiDataSourceType.LOCAL_AVERAGE_ONLY)
                .build();
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

    // 안전 리스크 체크 — KGS 가스사고 현황(주요 원인) + 가스 설비 보유 여부 + 리뷰/문의 키워드를 결합한 문구
    private String buildSafetyCheckText(
            AiOwnerMetricSnapshot snapshot,
            Optional<PublicDataService.GasAccidentSummary> gasSummary,
            SafetyKeywordSignal keywordSignal
    ) {
        boolean hasGasEquipment = snapshot.hasGasEquipment();
        boolean hasKeywords = !keywordSignal.detectedKeywords().isEmpty();
        String keywordPhrase = hasKeywords ? String.join("·", keywordSignal.detectedKeywords()) : null;

        boolean hasTopCause = gasSummary.isPresent() && gasSummary.get().topCause() != null
                && gasSummary.get().topCauseRatio() > 0;
        if (hasTopCause) {
            String topCause = gasSummary.get().topCause();
            String ratioText = formatRatio(gasSummary.get().topCauseRatio());
            StringBuilder text = new StringBuilder(String.format(
                    "최근 가스사고 통계에서 %s가 주요 원인으로 확인됩니다(약 %s%%).", topCause, ratioText));
            if (hasGasEquipment) {
                text.append(" 우리 가게는 가스 설비를 사용 중이므로 밸브·호스·환기 상태를 우선 점검하세요.");
            } else if (hasKeywords) {
                text.append(" 최근 리뷰/문의에서 ").append(keywordPhrase)
                        .append(" 키워드가 감지되어 환기 상태를 점검해보시길 권해드립니다.");
            } else {
                text.append(" 관련 설비를 우선 점검하세요.");
            }
            return text.toString();
        }

        if (gasSummary.isPresent()) {
            return String.format("최근 공개 가스사고 데이터 %d건 기준으로 확인됩니다. 정기 안전 점검을 권장드려요.", gasSummary.get().totalCount());
        }

        // KGS 데이터 자체를 못 가져온 경우 — 가스 설비/키워드 신호만으로 fallback 문구 구성
        if (hasGasEquipment && hasKeywords) {
            return String.format("가스 설비를 사용 중이고 최근 리뷰/문의에서 %s 키워드가 감지되었습니다. 밸브·호스·환기 상태를 점검해주세요.",
                    keywordPhrase);
        }
        if (hasGasEquipment) {
            return "가스 설비를 사용 중입니다. 밸브·호스 상태를 정기적으로 점검해주세요.";
        }
        if (hasKeywords) {
            return String.format("최근 리뷰/문의에서 %s 키워드가 감지되었습니다. 환기 상태를 점검해보시길 권해드립니다.", keywordPhrase);
        }
        return "정기 안전 점검 체크리스트를 확인해주세요.";
    }

    private String buildActivityAnomalyText(AiRiskLevel level, AiOwnerMetricSnapshot snapshot) {
        if (snapshot.monthlyPowerKwh() == null) {
            return "전력 사용량 실측값이 없어 동네 평균 기준으로만 판단하고 있습니다.";
        }
        return level == AiRiskLevel.NORMAL
                ? "업종 활동에 특이 변화가 감지되지 않았습니다."
                : "평소 대비 전력 사용 패턴에 변화가 감지됐습니다. 운영 상황을 확인해보세요.";
    }

    private boolean usedPublicData(String energySignal, Optional<PublicDataService.GasAccidentSummary> gasSummary) {
        return energySignal.startsWith("공공데이터") || gasSummary.isPresent();
    }

    // 소수 첫째 자리까지 표기 (예: 32.5, 30.0)
    private String formatRatio(double ratio) {
        return String.format("%.1f", ratio);
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
