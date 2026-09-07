package com.eeum.eeum.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "storage.s3")
public record S3StorageProperties(
        String bucket,
        String region,
        Duration uploadUrlExpiration,
        Duration downloadUrlExpiration,
        // 프론트가 아직 예전 외부 HTTPS URL을 보내는 전환 기간에만 켠다. 켜져 있는 동안은
        // 이미지 필드에 소유권 검증 없이 임의 외부 URL이 저장될 수 있다.
        Boolean allowLegacyHttpsUrl
) {

    public S3StorageProperties {
        bucket = bucket == null ? "" : bucket.trim();
        region = region == null || region.isBlank() ? "ap-northeast-2" : region.trim();
        uploadUrlExpiration = uploadUrlExpiration == null ? Duration.ofMinutes(10) : uploadUrlExpiration;
        downloadUrlExpiration = downloadUrlExpiration == null ? Duration.ofMinutes(10) : downloadUrlExpiration;
        // 기본값은 현재 동작 유지(허용)다. 프론트 전환이 끝나면 false로 내려 닫는다.
        allowLegacyHttpsUrl = allowLegacyHttpsUrl == null || allowLegacyHttpsUrl;
    }

    public boolean isConfigured() {
        return !bucket.isBlank();
    }
}
