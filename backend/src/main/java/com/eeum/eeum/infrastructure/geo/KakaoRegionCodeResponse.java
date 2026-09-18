package com.eeum.eeum.infrastructure.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// 카카오 coord2regioncode 응답 중 법정동 판별에 쓰는 필드만 받는다.
@JsonIgnoreProperties(ignoreUnknown = true)
record KakaoRegionCodeResponse(List<Document> documents) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Document(
            @JsonProperty("region_type") String regionType,
            String code
    ) {
    }
}
