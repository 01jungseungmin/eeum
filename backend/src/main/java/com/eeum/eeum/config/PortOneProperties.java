package com.eeum.eeum.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
        String apiSecret,
        String baseUrl
) {
}