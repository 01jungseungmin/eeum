package com.eeum.eeum.infrastructure.external.client;

import com.eeum.eeum.infrastructure.external.config.PublicDataProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 공공데이터포털 표준(ODCloud) API 공통 클라이언트.
 * 응답 형식: { "currentCount": n, "data": [ {...} ], ... }
 * 실패(키 누락/4xx/5xx/timeout/파싱 실패)는 빈 리스트로 처리해 상위 서비스가 fallback하게 한다.
 * 주의: serviceKey와 응답 전문은 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class PublicDataApiClient {

    private static final int DEFAULT_PER_PAGE = 500;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final PublicDataProperties properties;

    public PublicDataApiClient(
            @Qualifier("aiRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            PublicDataProperties properties
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    // 지정 엔드포인트에서 data[] 행을 조회 — 실패 시 빈 리스트 (fallback은 상위에서)
    public List<JsonNode> fetchRows(String endpointUrl, String apiName) {
        return fetchRows(endpointUrl, apiName, null);
    }

    // condition이 있으면 ODCloud 조건 검색 문법 cond[필드명::연산자]=값을 붙여 서버 측에서 미리 걸러 받는다.
    // (전체 건수가 수십만 건인 데이터셋에서 필터 없이 perPage만큼만 받으면 원하는 지역이 아예 안 들어올 수 있다)
    public List<JsonNode> fetchRows(String endpointUrl, String apiName, RegionCondition condition) {
        if (!properties.hasServiceKey() || endpointUrl == null || endpointUrl.isBlank()) {
            log.debug("[PUBLIC-DATA] {} 미설정 (serviceKey 또는 URL 없음) — fallback", apiName);
            return List.of();
        }
        // aiRestClient는 baseUrl이 없는 Bean이라, 엔드포인트 URL에 scheme(http/https)이 빠지면
        // "URI is not absolute" 같은 알아보기 힘든 예외로 이어진다 — 설정 실수를 바로 알 수 있도록 사전 검증한다.
        if (!endpointUrl.startsWith("http://") && !endpointUrl.startsWith("https://")) {
            log.warn("[PUBLIC-DATA][{}] 엔드포인트 URL에 http(s):// 스킴이 없습니다 — 환경변수 설정을 확인하세요", apiName);
            return List.of();
        }
        try {
            URI uri = buildUri(endpointUrl, condition);
            log.info("[PUBLIC-DATA][{}] request page=1, perPage={}, hasCondition={}, serviceKey=****",
                    apiName, DEFAULT_PER_PAGE, condition != null);
            String raw = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                log.info("[PUBLIC-DATA][{}] 응답에 데이터 없음 (currentCount={}, totalCount={})",
                        apiName, root.path("currentCount").asInt(-1), root.path("totalCount").asInt(-1));
                return List.of();
            }
            log.info("[PUBLIC-DATA][{}] currentCount={}, totalCount={}",
                    apiName, root.path("currentCount").asInt(-1), root.path("totalCount").asInt(-1));
            List<JsonNode> rows = new ArrayList<>();
            data.forEach(rows::add);

            // 기관마다 실제 응답 컬럼명이 달라 매핑이 어긋나기 쉽다 — 첫 행의 필드명만 info로,
            // 행 전체(고객/문의 본문 등 민감정보 포함 가능성)는 debug로만 남긴다.
            if (!rows.isEmpty()) {
                JsonNode first = rows.get(0);
                List<String> fieldNames = new ArrayList<>();
                first.fieldNames().forEachRemaining(fieldNames::add);
                log.info("[PUBLIC-DATA][{}] firstRowFields={}", apiName, fieldNames);
                log.debug("[PUBLIC-DATA][{}] firstRow={}", apiName, first);
            }
            return rows;
        } catch (Exception e) {
            // 응답 전문/키를 남기지 않도록 예외 요약만 기록 (인코딩 수정 이후로는 key가 메시지에 섞일 일이 없다)
            log.warn("[PUBLIC-DATA][{}] 호출 실패 — fallback: {} - {}",
                    apiName, e.getClass().getSimpleName(), e.getMessage());
            return List.of();
        }
    }

    private URI buildUri(String endpointUrl, RegionCondition condition) {
        // Decoding 키(원문)에는 +, /, = 같은 문자가 그대로 들어있어 build(true)의 QUERY_PARAM 검증을
        // 통과하지 못한다 — URLEncoder로 직접 인코딩한 뒤에만 "이미 인코딩된 값"으로 전달해야 한다.
        // (특히 +를 %2B로 바꿔주지 않으면 서버 측에서 공백으로 오디코딩되어 인증 실패로 이어진다)
        String encodedServiceKey = URLEncoder.encode(properties.serviceKey(), StandardCharsets.UTF_8);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(endpointUrl)
                .queryParam("serviceKey", encodedServiceKey)
                .queryParam("page", 1)
                .queryParam("perPage", DEFAULT_PER_PAGE)
                .queryParam("returnType", "JSON");
        if (condition != null) {
            // ODCloud 조건 검색: cond[필드명::연산자]=값 — 파라미터명 자체에 한글/대괄호/콜론이 들어가므로
            // 이름과 값 전부 URLEncoder로 인코딩한 뒤 build(true)로 넘긴다 (raw 대괄호는 QUERY_PARAM 검증에서 거부됨)
            String encodedField = URLEncoder.encode(condition.field(), StandardCharsets.UTF_8);
            String encodedValue = URLEncoder.encode(condition.value(), StandardCharsets.UTF_8);
            String condParamName = "cond%5B" + encodedField + "%3A%3A" + condition.operator() + "%5D";
            builder.queryParam(condParamName, encodedValue);
        }
        return builder.build(true).toUri();
    }

    // ODCloud 조건 검색 문법(cond[필드명::연산자]=값)을 나타내는 값 객체
    public record RegionCondition(String field, String operator, String value) {
        public static RegionCondition eq(String field, String value) {
            return new RegionCondition(field, "EQ", value);
        }
    }
}
