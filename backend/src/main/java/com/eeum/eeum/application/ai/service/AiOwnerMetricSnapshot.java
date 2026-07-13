package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.domain.ai.entity.AiOwnerMetricInput;
import com.eeum.eeum.domain.ai.enums.AiMetricType;
import com.eeum.eeum.domain.ai.repository.AiOwnerMetricInputRepository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// 절감 계획(AiSavingPlanCommandExecutor)과 운영 위험 카드(AiOperationRiskService)가 공통으로 쓰는
// 사장님 실측값 묶음 — 여러 AiMetricType을 매번 따로 조회하지 않고 한 번에 로딩해서 재사용한다.
record AiOwnerMetricSnapshot(
        BigDecimal monthlyPowerKwh,
        BigDecimal monthlyPowerBill,
        BigDecimal openHoursPerDay,
        BigDecimal businessDaysPerMonth,
        BigDecimal airConditionerCount,
        BigDecimal refrigeratorCount,
        BigDecimal gasEquipmentCount,
        BigDecimal monthlyGasBill
) {

    static final List<AiMetricType> TRACKED_TYPES = List.of(
            AiMetricType.MONTHLY_POWER_KWH,
            AiMetricType.MONTHLY_POWER_BILL,
            AiMetricType.OPEN_HOURS_PER_DAY,
            AiMetricType.BUSINESS_DAYS_PER_MONTH,
            AiMetricType.AIR_CONDITIONER_COUNT,
            AiMetricType.REFRIGERATOR_COUNT,
            AiMetricType.GAS_EQUIPMENT_COUNT,
            AiMetricType.MONTHLY_GAS_BILL
    );

    // 당월 + 전월 데이터를 한 쿼리로 조회해서 스냅샷을 구성 — AiOperationRiskService/AiSavingPlanCommandExecutor 공용
    static AiOwnerMetricSnapshot load(AiOwnerMetricInputRepository repository, Long storeId) {
        List<String> yearMonths = List.of(YearMonth.now().toString(), YearMonth.now().minusMonths(1).toString());
        List<AiOwnerMetricInput> inputs = repository
                .findByStore_StoreIdAndMetricTypeInAndYearMonthIn(storeId, TRACKED_TYPES, yearMonths);
        return from(inputs);
    }

    // 당월 값을 우선하고, 당월에 없는 타입만 전월 값으로 채운다 (yearMonth 내림차순 정렬 후 첫 값 채택)
    static AiOwnerMetricSnapshot from(List<AiOwnerMetricInput> inputs) {
        Map<AiMetricType, BigDecimal> latest = new EnumMap<>(AiMetricType.class);
        inputs.stream()
                .sorted(Comparator.comparing(AiOwnerMetricInput::getYearMonth).reversed())
                .forEach(input -> latest.putIfAbsent(input.getMetricType(), input.getValue()));

        return new AiOwnerMetricSnapshot(
                latest.get(AiMetricType.MONTHLY_POWER_KWH),
                latest.get(AiMetricType.MONTHLY_POWER_BILL),
                latest.get(AiMetricType.OPEN_HOURS_PER_DAY),
                latest.get(AiMetricType.BUSINESS_DAYS_PER_MONTH),
                latest.get(AiMetricType.AIR_CONDITIONER_COUNT),
                latest.get(AiMetricType.REFRIGERATOR_COUNT),
                latest.get(AiMetricType.GAS_EQUIPMENT_COUNT),
                latest.get(AiMetricType.MONTHLY_GAS_BILL)
        );
    }

    // 실측 기반 분석 여부 — 전력 사용량 또는 전기요금 중 하나라도 있어야 "정밀 데이터"로 본다
    boolean hasPreciseData() {
        return monthlyPowerKwh != null || monthlyPowerBill != null;
    }

    boolean hasAirConditioner() {
        return isPositive(airConditionerCount);
    }

    boolean hasRefrigerator() {
        return isPositive(refrigeratorCount);
    }

    boolean hasGasEquipment() {
        return isPositive(gasEquipmentCount);
    }

    boolean isLongOpenHours() {
        return openHoursPerDay != null && openHoursPerDay.compareTo(BigDecimal.valueOf(10)) >= 0;
    }

    boolean isHighPowerUsage() {
        return monthlyPowerKwh != null && monthlyPowerKwh.compareTo(BigDecimal.valueOf(700)) >= 0;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
