package com.eeum.eeum.infrastructure.external.dto;

// 한국전력공사_산업분류별 법정동별 전력사용량 정규화 DTO
public record RegionPowerUsage(
        String period,          // 기준년월 (예: 2026-05)
        String region,          // 시도/시군구/법정동 표기
        String industry,        // 산업분류
        Long customerCount,     // 고객호수
        Double usageKwh,        // 판매량(kWh)
        Double chargeAmount,    // 판매요금(원)
        Double averageUsageKwh  // 호당 평균 사용량
) {
}
