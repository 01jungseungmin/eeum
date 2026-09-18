package com.eeum.eeum.infrastructure.geo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 로컬 API 설정. rest-api-key는 GeocodingService와 같은 키를 공유한다.
 * local-base-url은 계약 테스트가 로컬 스텁으로 돌리려고 둔 값이라 운영에서는 비워 둔다.
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoLocalProperties(
        String restApiKey,
        String localBaseUrl
) {

    private static final String DEFAULT_LOCAL_BASE_URL = "https://dapi.kakao.com";

    public KakaoLocalProperties {
        if (localBaseUrl == null || localBaseUrl.isBlank()) {
            localBaseUrl = DEFAULT_LOCAL_BASE_URL;
        }
    }

    public boolean hasRestApiKey() {
        return restApiKey != null && !restApiKey.isBlank();
    }
}
