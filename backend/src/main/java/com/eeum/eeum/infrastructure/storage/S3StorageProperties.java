package com.eeum.eeum.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "storage.s3")
public record S3StorageProperties(
        String bucket,
        String region,
        Duration uploadUrlExpiration,
        Duration downloadUrlExpiration
) {

    public S3StorageProperties {
        bucket = bucket == null ? "" : bucket.trim();
        region = region == null || region.isBlank() ? "ap-northeast-2" : region.trim();
        uploadUrlExpiration = uploadUrlExpiration == null ? Duration.ofMinutes(10) : uploadUrlExpiration;
        downloadUrlExpiration = downloadUrlExpiration == null ? Duration.ofMinutes(10) : downloadUrlExpiration;
    }

    public boolean isConfigured() {
        return !bucket.isBlank();
    }
}
