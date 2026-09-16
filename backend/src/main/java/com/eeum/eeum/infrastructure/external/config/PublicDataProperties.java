package com.eeum.eeum.infrastructure.external.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공공데이터포털 OpenAPI 설정.
 * data.go.kr은 계정당 일반 인증키 1개를 API별 활용신청으로 공유하는 구조 —
 * serviceKey 하나로 3종 API를 호출한다 (필요 시 API별 키 분리 가능).
 * 키/URL이 없어도 서버는 기동되며, 해당 데이터 기능만 fallback 처리된다.
 */
@ConfigurationProperties(prefix = "public-data")
public record PublicDataProperties(
        String serviceKey,
        // 한국전력공사_산업분류별 법정동별 전력사용량 (15104908) 엔드포인트
        String kepcoIndustryUrl,
        // 한국전력거래소_행정구역별 에너지사용량 통합데이터 (15156136) 엔드포인트
        String kpxRegionEnergyUrl,
        // 한국가스안전공사_가스사고 현황 (15067796) 엔드포인트
        String kgsGasAccidentUrl
) {
    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
