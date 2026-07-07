package com.eeum.eeum.domain.ai.enums;

public enum AiMetricType {

    // 전력
    MONTHLY_POWER_KWH,        // 월 전력 사용량 kWh
    MONTHLY_POWER_BILL,       // 월 전기요금 원

    // 영업 정보
    OPEN_HOURS_PER_DAY,       // 하루 영업시간
    BUSINESS_DAYS_PER_MONTH,  // 월 영업일수

    // 설비
    AIR_CONDITIONER_COUNT,    // 에어컨 대수
    REFRIGERATOR_COUNT,       // 냉장/냉동 설비 대수
    HEATING_EQUIPMENT_COUNT,  // 난방 설비 대수
    GAS_EQUIPMENT_COUNT,      // 가스 사용 설비 대수
    EQUIPMENT_COUNT,          // 전체 주요 설비 대수

    // 가스/안전
    MONTHLY_GAS_USAGE_M3,     // 월 가스 사용량
    MONTHLY_GAS_BILL          // 월 가스요금
}
