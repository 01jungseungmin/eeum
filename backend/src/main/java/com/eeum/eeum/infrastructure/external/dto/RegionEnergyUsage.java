package com.eeum.eeum.infrastructure.external.dto;

// 한국전력거래소_행정구역별 에너지사용량 통합데이터 정규화 DTO
public record RegionEnergyUsage(
        String period,     // 기준 기간
        String region,     // 행정구역
        String energyType, // 전기/가스/열
        Double usage       // 사용량
) {
}
