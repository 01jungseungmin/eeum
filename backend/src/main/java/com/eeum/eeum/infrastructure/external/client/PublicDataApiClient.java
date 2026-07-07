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
        if (!properties.hasServiceKey() || endpointUrl == null || endpointUrl.isBlank()) {
            log.debug("[PUBLIC-DATA] {} 미설정 (serviceKey 또는 URL 없음) — fallback", apiName);
            return List.of();
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString(endpointUrl)
                    .queryParam("serviceKey", properties.serviceKey())
                    .queryParam("page", 1)
                    .queryParam("perPage", DEFAULT_PER_PAGE)
                    .queryParam("returnType", "JSON")
                    .build(true) // serviceKey는 이미 인코딩된 상태로 전달
                    .toUri();
            String raw = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                log.info("[PUBLIC-DATA] {} 응답에 데이터 없음", apiName);
                return List.of();
            }
            List<JsonNode> rows = new ArrayList<>();
            data.forEach(rows::add);
            return rows;
        } catch (Exception e) {
            // 응답 전문/키를 남기지 않도록 예외 요약만 기록
            log.warn("[PUBLIC-DATA] {} 호출 실패 — fallback: {}", apiName, e.getClass().getSimpleName());
            return List.of();
        }
    }
}
