package com.eeum.eeum.application.search.service;

import com.eeum.eeum.application.search.config.PopularSearchProperties;
import com.eeum.eeum.application.search.dto.response.PopularSearchKeywordResponseDto;
import com.eeum.eeum.application.search.enums.PopularSearchScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PopularSearchService {

    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");

    // 중복키 생성과 점수 증가를 분리하면 동시 요청이 모두 "첫 검색"으로 판단할 수 있다.
    private static final DefaultRedisScript<Long> RECORD_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('set', KEYS[1], '1', 'NX', 'EX', tonumber(ARGV[2])) == false then
                return 0
            end
            redis.call('zincrby', KEYS[2], 1, ARGV[1])
            if redis.call('ttl', KEYS[2]) < 0 then
                redis.call('expire', KEYS[2], tonumber(ARGV[3]))
            end
            redis.call('zincrby', KEYS[3], 1, ARGV[1])
            if redis.call('ttl', KEYS[3]) < 0 then
                redis.call('expire', KEYS[3], tonumber(ARGV[3]))
            end
            return 1
            """,
            Long.class
    );

    // 24개 시간 버킷을 Redis 내부에서 합쳐 1분간 캐시한다. 원시 점수는 외부에 내보내지 않는다.
    private static final DefaultRedisScript<List> READ_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('exists', KEYS[1]) == 0 then
                redis.call('zunionstore', KEYS[1], #KEYS - 1, unpack(KEYS, 2))
                redis.call('expire', KEYS[1], tonumber(ARGV[1]))
            end
            return redis.call('zrevrange', KEYS[1], 0, tonumber(ARGV[2]) - 1, 'WITHSCORES')
            """,
            List.class
    );

    private final StringRedisTemplate redisTemplate;
    private final PopularSearchProperties properties;

    /** 검색이 정상적으로 끝난 뒤 호출한다. 집계 실패가 본래 검색 응답을 실패시키면 안 된다. */
    public void record(PopularSearchScope scope, String rawKeyword, String viewerKey) {
        if (scope == null || scope == PopularSearchScope.ALL) {
            throw new IllegalArgumentException("집계 범위는 개별 검색 범위여야 합니다.");
        }

        String keyword = normalizeAndValidate(rawKeyword);
        if (keyword == null || viewerKey == null || viewerKey.isBlank()) {
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(KOREA_ZONE_ID);
        try {
            redisTemplate.execute(
                    RECORD_SCRIPT,
                    List.of(
                            duplicateKey(scope, viewerKey, keyword),
                            bucketKey(scope, now),
                            bucketKey(PopularSearchScope.ALL, now)
                    ),
                    keyword,
                    String.valueOf(properties.duplicateTtl().toSeconds()),
                    String.valueOf(properties.bucketTtl().toSeconds())
            );
        } catch (RuntimeException exception) {
            log.warn("인기 검색어 집계 실패: scope={}", scope, exception);
        }
    }

    public List<PopularSearchKeywordResponseDto> getPopularKeywords(PopularSearchScope scope, int limit) {
        if (scope == null || limit < 1) {
            throw new IllegalArgumentException("인기 검색어 조회 조건이 올바르지 않습니다.");
        }

        ZonedDateTime now = ZonedDateTime.now(KOREA_ZONE_ID);
        List<String> keys = new ArrayList<>();
        keys.add(cacheKey(scope));
        for (int hour = 0; hour < properties.windowHours(); hour++) {
            keys.add(bucketKey(scope, now.minusHours(hour)));
        }

        try {
            List<?> values = redisTemplate.execute(
                    READ_SCRIPT,
                    keys,
                    String.valueOf(properties.cacheTtl().toSeconds()),
                    String.valueOf(limit)
            );
            return toResponses(values);
        } catch (RuntimeException exception) {
            log.warn("인기 검색어 조회 실패: scope={}", scope, exception);
            return List.of();
        }
    }

    private List<PopularSearchKeywordResponseDto> toResponses(List<?> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        List<PopularSearchKeywordResponseDto> responses = new ArrayList<>();
        for (int index = 0; index + 1 < values.size(); index += 2) {
            responses.add(PopularSearchKeywordResponseDto.builder()
                    .rank(responses.size() + 1)
                    .keyword(String.valueOf(values.get(index)))
                    .build());
        }
        return responses;
    }

    private String normalizeAndValidate(String rawKeyword) {
        if (rawKeyword == null) {
            return null;
        }

        String keyword = rawKeyword.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        Set<String> bannedKeywords = properties.normalizedBannedKeywords();
        if (keyword.length() < properties.minKeywordLength()
                || keyword.length() > properties.maxKeywordLength()
                || bannedKeywords.contains(keyword)) {
            return null;
        }
        return keyword;
    }

    private String bucketKey(PopularSearchScope scope, ZonedDateTime time) {
        return "search:popular:" + scope.name().toLowerCase(Locale.ROOT) + ":" + HOUR_FORMATTER.format(time);
    }

    private String cacheKey(PopularSearchScope scope) {
        return "search:popular:cache:" + scope.name().toLowerCase(Locale.ROOT);
    }

    private String duplicateKey(PopularSearchScope scope, String viewerKey, String keyword) {
        return "search:popular:dedup:" + scope.name().toLowerCase(Locale.ROOT) + ":"
                + sha256(viewerKey + "\n" + keyword);
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte part : hash) {
                result.append(String.format("%02x", part));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
