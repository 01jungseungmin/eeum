package com.eeum.eeum.application.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "search.popular")
public record PopularSearchProperties(
        Duration bucketTtl,
        Duration duplicateTtl,
        Duration cacheTtl,
        Integer windowHours,
        Integer minKeywordLength,
        Integer maxKeywordLength,
        String bannedKeywords
) {

    public PopularSearchProperties {
        bucketTtl = bucketTtl == null ? Duration.ofHours(25) : bucketTtl;
        duplicateTtl = duplicateTtl == null ? Duration.ofMinutes(10) : duplicateTtl;
        cacheTtl = cacheTtl == null ? Duration.ofMinutes(1) : cacheTtl;
        windowHours = windowHours == null ? 24 : windowHours;
        minKeywordLength = minKeywordLength == null ? 2 : minKeywordLength;
        maxKeywordLength = maxKeywordLength == null ? 50 : maxKeywordLength;
        bannedKeywords = bannedKeywords == null ? "" : bannedKeywords;

        if (bucketTtl.isNegative() || bucketTtl.isZero()
                || duplicateTtl.isNegative() || duplicateTtl.isZero()
                || cacheTtl.isNegative() || cacheTtl.isZero()
                || windowHours < 1 || minKeywordLength < 1 || maxKeywordLength < minKeywordLength) {
            throw new IllegalArgumentException("인기 검색어 설정값이 올바르지 않습니다.");
        }
    }

    public Set<String> normalizedBannedKeywords() {
        return Arrays.stream(bannedKeywords.split(","))
                .map(this::normalize)
                .filter(keyword -> !keyword.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
