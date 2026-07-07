package com.eeum.eeum.infrastructure.external;

import com.eeum.eeum.common.util.RedisUtil;
import com.eeum.eeum.infrastructure.external.client.PublicDataApiClient;
import com.eeum.eeum.infrastructure.external.config.PublicDataProperties;
import com.eeum.eeum.infrastructure.external.dto.GasAccidentStat;
import com.eeum.eeum.infrastructure.external.dto.RegionEnergyUsage;
import com.eeum.eeum.infrastructure.external.dto.RegionPowerUsage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 공공데이터 3종 조회 파사드 — 정규화 + Redis 일 단위 캐싱 + fallback.
 * 컬럼명이 기관마다 달라 대표 후보 키로 방어적으로 매핑한다.
 * 외부 장애/키 누락 시 빈 결과를 반환하고, 상위(AI 리포트)는 기존 로직으로 fallback한다.
 */
@Slf4j
@Service
public class PublicDataService {

    private static final long CACHE_TTL_SECONDS = 24 * 60 * 60; // 하루 캐싱

    private final PublicDataApiClient apiClient;
    private final PublicDataProperties properties;
    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    public PublicDataService(PublicDataApiClient apiClient, PublicDataProperties properties,
                             RedisUtil redisUtil, ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.properties = properties;
        this.redisUtil = redisUtil;
        this.objectMapper = objectMapper;
    }

    // 산업분류별 법정동별 전력사용량 — 지역 키워드(시군구/법정동명 일부)로 필터
    public List<RegionPowerUsage> getIndustryPowerUsages(String regionKeyword) {
        return cached("public-data:kepco-industry:" + safeKey(regionKeyword),
                new TypeReference<List<RegionPowerUsage>>() {},
                () -> apiClient.fetchRows(properties.kepcoIndustryUrl(), "KEPCO 산업분류별 전력사용량").stream()
                        .filter(row -> matchesRegion(row, regionKeyword))
                        .map(this::toPowerUsage)
                        .filter(usage -> usage.usageKwh() != null)
                        .limit(50)
                        .toList());
    }

    // 행정구역별 에너지사용량 — 지역 에너지 활동 보조 지표
    public List<RegionEnergyUsage> getRegionEnergyUsages(String regionKeyword) {
        return cached("public-data:kpx-region:" + safeKey(regionKeyword),
                new TypeReference<List<RegionEnergyUsage>>() {},
                () -> apiClient.fetchRows(properties.kpxRegionEnergyUrl(), "KPX 행정구역별 에너지사용량").stream()
                        .filter(row -> matchesRegion(row, regionKeyword))
                        .map(this::toEnergyUsage)
                        .filter(usage -> usage.usage() != null)
                        .limit(50)
                        .toList());
    }

    // 가스사고 현황 — 안전 조기경보용 (지역 무관 전국 통계)
    public List<GasAccidentStat> getGasAccidentStats() {
        return cached("public-data:kgs-accident",
                new TypeReference<List<GasAccidentStat>>() {},
                () -> apiClient.fetchRows(properties.kgsGasAccidentUrl(), "KGS 가스사고 현황").stream()
                        .map(this::toGasAccident)
                        .limit(100)
                        .toList());
    }

    // 가스사고 요약 — 총 건수 + 최다 원인 (없으면 empty)
    public Optional<GasAccidentSummary> getGasAccidentSummary() {
        List<GasAccidentStat> stats = getGasAccidentStats();
        if (stats.isEmpty()) {
            return Optional.empty();
        }
        int total = stats.stream().mapToInt(GasAccidentStat::accidentCount).sum();
        String topCause = stats.stream()
                .filter(stat -> stat.cause() != null && !stat.cause().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(GasAccidentStat::cause,
                        java.util.stream.Collectors.summingInt(GasAccidentStat::accidentCount)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        return Optional.of(new GasAccidentSummary(total, topCause));
    }

    public record GasAccidentSummary(int totalCount, String topCause) {
    }

    // ===================== 내부 유틸 =====================

    // 캐시 우선 조회 — API 실패로 빈 결과면 캐시하지 않아 다음 요청에서 재시도
    private <T> List<T> cached(String cacheKey, TypeReference<List<T>> typeRef, Supplier<List<T>> loader) {
        try {
            Optional<String> cachedJson = redisUtil.get(cacheKey);
            if (cachedJson.isPresent()) {
                return objectMapper.readValue(cachedJson.get(), typeRef);
            }
        } catch (Exception e) {
            log.debug("[PUBLIC-DATA] 캐시 조회 실패 — API 조회로 진행: key={}", cacheKey);
        }
        List<T> loaded = loader.get();
        if (!loaded.isEmpty()) {
            try {
                redisUtil.set(cacheKey, objectMapper.writeValueAsString(loaded), CACHE_TTL_SECONDS);
            } catch (Exception e) {
                log.debug("[PUBLIC-DATA] 캐시 저장 실패 — 무시: key={}", cacheKey);
            }
        }
        return loaded;
    }

    private boolean matchesRegion(JsonNode row, String regionKeyword) {
        if (regionKeyword == null || regionKeyword.isBlank()) {
            return true;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = row.fields();
        while (fields.hasNext()) {
            JsonNode value = fields.next().getValue();
            if (value.isTextual() && value.asText().contains(regionKeyword)) {
                return true;
            }
        }
        return false;
    }

    private RegionPowerUsage toPowerUsage(JsonNode row) {
        Long customerCount = firstLong(row, "고객호수", "custCnt", "cust_cnt", "호수");
        Double usage = firstDouble(row, "판매량", "사용량", "powerUsage", "usekwh", "use_kwh", "판매전력량");
        return new RegionPowerUsage(
                firstText(row, "기준년월", "년월", "yearMonth", "baseYm", "기준일자", "연도"),
                firstText(row, "법정동", "시군구", "시도", "지역", "cityNm", "행정구역"),
                firstText(row, "산업분류", "업종", "계약종별", "industryNm", "산업분류명"),
                customerCount,
                usage,
                firstDouble(row, "판매요금", "요금", "chargeAmt"),
                (usage != null && customerCount != null && customerCount > 0) ? usage / customerCount : null);
    }

    private RegionEnergyUsage toEnergyUsage(JsonNode row) {
        return new RegionEnergyUsage(
                firstText(row, "기준년월", "년월", "연도", "yearMonth", "기준일자"),
                firstText(row, "행정구역", "시군구", "시도", "지역"),
                firstText(row, "에너지원", "에너지종류", "구분", "energyType"),
                firstDouble(row, "사용량", "에너지사용량", "usage", "전기사용량"));
    }

    private GasAccidentStat toGasAccident(JsonNode row) {
        Integer count = firstInt(row, "사고건수", "건수", "count", "accidentCount");
        return new GasAccidentStat(
                firstText(row, "기준년월", "발생년월", "연도", "년도", "발생일자"),
                firstText(row, "사고원인", "원인", "cause"),
                firstText(row, "가스종류", "가스명", "gasType"),
                count != null ? count : 1); // 건 단위 목록형 데이터면 행당 1건으로 집계
    }

    private String firstText(JsonNode row, String... keys) {
        for (String key : keys) {
            JsonNode node = row.get(key);
            if (node != null && !node.isNull() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return null;
    }

    private Double firstDouble(JsonNode row, String... keys) {
        String text = firstText(row, keys);
        if (text == null) {
            return null;
        }
        try {
            return Double.parseDouble(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long firstLong(JsonNode row, String... keys) {
        Double value = firstDouble(row, keys);
        return value != null ? value.longValue() : null;
    }

    private Integer firstInt(JsonNode row, String... keys) {
        Double value = firstDouble(row, keys);
        return value != null ? value.intValue() : null;
    }

    private String safeKey(String keyword) {
        return keyword == null || keyword.isBlank() ? "all" : keyword.replaceAll("\\s+", "");
    }
}
