package com.eeum.eeum.infrastructure.external.dto;

// 한국가스안전공사_가스사고 현황 정규화 DTO
public record GasAccidentStat(
        String period,       // 기준년월/연도
        String cause,        // 사고 원인
        String gasType,      // 가스 종류
        int accidentCount    // 사고 건수
) {
}
