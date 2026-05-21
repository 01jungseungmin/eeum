package com.eeum.eeum.application.region.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class KakaoAddressResponseDto {

    private List<Document> documents;

    @Getter
    @NoArgsConstructor
    public static class Document {

        @JsonProperty("address_name")
        private String addressName;

        private String x; // longitude, 경도

        private String y; // latitude, 위도
    }
}