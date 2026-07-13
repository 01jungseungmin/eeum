package com.eeum.eeum.infrastructure.external;

import com.eeum.eeum.common.util.RedisUtil;
import com.eeum.eeum.infrastructure.external.client.PublicDataApiClient;
import com.eeum.eeum.infrastructure.external.config.PublicDataProperties;
import com.eeum.eeum.infrastructure.external.dto.RegionEnergyUsage;
import com.eeum.eeum.infrastructure.external.dto.RegionPowerUsage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
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

    // 카페/음식점 업종과 가까운 산업분류명(중) 우선순위 — 동일 지역 내 복수 행이 있을 때 대표값으로 우선 채택
    private static final List<String> PREFERRED_INDUSTRY_KEYWORDS =
            List.of("음식점", "주점", "숙박 및 음식점업", "숙박");

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

    // 산업분류별 법정동별 전력사용량 — 지역 키워드(시군구)로 서버 측 조건 검색 후 클라이언트에서 한 번 더 검증
    // (전체 55만 건 규모 데이터셋이라 조건 없이 perPage만 받으면 원하는 지역이 아예 안 들어올 수 있다)
    public List<RegionPowerUsage> getIndustryPowerUsages(String regionKeyword) {
        return cached("public-data:kepco-industry:" + safeKey(regionKeyword),
                new TypeReference<List<RegionPowerUsage>>() {},
                () -> {
                    boolean hasRegion = regionKeyword != null && !regionKeyword.isBlank();
                    List<JsonNode> rawRows = hasRegion
                            ? apiClient.fetchRows(properties.kepcoIndustryUrl(), "KEPCO 산업분류별 전력사용량",
                                    PublicDataApiClient.RegionCondition.eq("시군구", regionKeyword))
                            : apiClient.fetchRows(properties.kepcoIndustryUrl(), "KEPCO 산업분류별 전력사용량");

                    List<RegionPowerUsage> usages = rawRows.stream()
                            .filter(row -> matchesRegion(row, regionKeyword))
                            .map(this::toPowerUsage)
                            .filter(usage -> usage.usageKwh() != null)
                            .sorted(Comparator.comparingInt(this::industryPreferenceRank))
                            .limit(50)
                            .toList();

                    log.info("[PUBLIC-DATA][KEPCO] regionKeyword={}, matchedRows={}, firstRegion={}, firstIndustry={}",
                            regionKeyword, usages.size(),
                            usages.isEmpty() ? null : usages.get(0).region(),
                            usages.isEmpty() ? null : usages.get(0).industry());
                    return usages;
                });
    }

    // 선호 업종(음식점/주점/숙박)을 우선 채택 — 순위가 낮을수록(0) 먼저 온다
    private int industryPreferenceRank(RegionPowerUsage usage) {
        if (usage.industry() == null) {
            return 1;
        }
        return PREFERRED_INDUSTRY_KEYWORDS.stream().anyMatch(usage.industry()::contains) ? 0 : 1;
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

    // KGS 가스사고 현황은 "행 = 사고원인"이 아니라 "행(기간) = 원인별 건수 컬럼들"인 wide-format 응답이다.
    // (예: 한 행에 공급자취급부주의/사용자취급부주의/시설미비/... 컬럼이 각각 숫자로 존재)
    private static final List<String> AGING_CAUSE_FIELD_ALIASES = List.of("제품노후(고장)", "제품노후", "제품노후고장");
    private static final List<String> OTHER_CAUSE_FIELD_ALIASES = List.of("기타(1-3급)", "기타");

    // 가스사고 현황 원본 행 — 지역 무관 전국 통계, wide-format 그대로 캐싱
    private List<JsonNode> getGasAccidentRows() {
        return cached("public-data:kgs-accident",
                new TypeReference<List<JsonNode>>() {},
                () -> apiClient.fetchRows(properties.kgsGasAccidentUrl(), "KGS 가스사고 현황"));
    }

    // 가스사고 요약 — 공개 데이터 건수(publicAccidentCount 근거) + 원인별 집계 + 최다 원인/비율 (없으면 empty)
    public Optional<GasAccidentSummary> getGasAccidentSummary() {
        List<JsonNode> rows = getGasAccidentRows();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(buildGasAccidentInsight(rows));
    }

    private GasAccidentSummary buildGasAccidentInsight(List<JsonNode> rows) {
        Map<String, Integer> causeSums = new LinkedHashMap<>();
        causeSums.put("공급자취급부주의", sumCause(rows, "공급자취급부주의"));
        causeSums.put("사용자취급부주의", sumCause(rows, "사용자취급부주의"));
        causeSums.put("시설미비", sumCause(rows, "시설미비"));
        causeSums.put("제품노후(고장)", sumCause(rows, AGING_CAUSE_FIELD_ALIASES.toArray(new String[0])));
        causeSums.put("타공사", sumCause(rows, "타공사"));
        causeSums.put("교통사고", sumCause(rows, "교통사고"));
        causeSums.put("기타", sumCause(rows, OTHER_CAUSE_FIELD_ALIASES.toArray(new String[0])));

        int totalCauseCount = causeSums.values().stream().mapToInt(Integer::intValue).sum();

        Map.Entry<String, Integer> top = causeSums.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        String topCause = top != null ? top.getKey() : null;
        int topCauseCount = top != null ? top.getValue() : 0;
        double topCauseRatio = (top != null && totalCauseCount > 0)
                ? Math.round(topCauseCount * 1000.0 / totalCauseCount) / 10.0 // 소수 첫째 자리 반올림
                : 0.0;

        // publicAccidentCount 근거는 rows.size()(공개 데이터 건수) — 원인별 합계(totalCauseCount)와는 별개 지표
        log.info("[PUBLIC-DATA][KGS] accidentCount={}, causeSums={}, topCause={}, topCauseRatio={}",
                rows.size(), causeSums, topCause, topCauseRatio);

        return new GasAccidentSummary(rows.size(), causeSums, topCause, topCauseCount, topCauseRatio);
    }

    private int sumCause(List<JsonNode> rows, String... fieldNames) {
        return rows.stream().mapToInt(row -> readInt(row, fieldNames)).sum();
    }

    // 숫자/문자열("1,234", "-", 공백, null) 어떤 형태로 와도 안전하게 정수로 변환 — 실패 시 0
    private int readInt(JsonNode row, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = row.get(fieldName);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.asInt();
            }
            String text = value.asText();
            if (text == null || text.isBlank() || "-".equals(text.trim())) {
                return 0;
            }
            try {
                return Integer.parseInt(text.replace(",", "").trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    // publicAccidentCount(rows.size() 기준 공개 데이터 건수) + 원인별 집계 + 최다 원인/비율
    public record GasAccidentSummary(int totalCount, Map<String, Integer> causeBreakdown,
                                      String topCause, int topCauseCount, double topCauseRatio) {
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
                firstText(row, "기준년월", "년월", "yearMonth", "baseYm", "기준일자", "연도", "년도"),
                firstText(row, "법정동", "시군구", "시도", "지역", "cityNm", "행정구역", "읍면동(법정동)"),
                // KEPCO 산업분류별 전력사용량은 "산업분류명(중)"/"산업분류명(대)"로 내려온다 — (중)이 더 구체적이라 우선
                firstText(row, "산업분류명(중)", "산업분류명(대)", "산업분류", "업종", "계약종별", "industryNm", "산업분류명"),
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

    private String safeKey(String keyword) {
        return keyword == null || keyword.isBlank() ? "all" : keyword.replaceAll("\\s+", "");
    }
}
