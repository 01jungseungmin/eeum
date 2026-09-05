package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.response.UnreadCountResponseDto;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnreadSnapshotRebuilder {

    private static final DefaultRedisScript<Long> DELETE_IF_VALUE_MATCHES =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then "
                            + "return redis.call('del', KEYS[1]) "
                            + "else return 0 end",
                    Long.class
            );

    private static final DefaultRedisScript<Long> REPLACE_IF_CURRENT_GENERATION = new DefaultRedisScript<>(
            "local current = redis.call('get', KEYS[3]) "
                    + "if current and tonumber(current) ~= tonumber(ARGV[1]) then return 0 end "
                    + "if not current and tonumber(ARGV[1]) ~= 0 then return 0 end "
                    + "redis.call('set', KEYS[1], ARGV[2]) "
                    + "redis.call('del', KEYS[2]) "
                    + "for i = 3, #ARGV, 2 do redis.call('hset', KEYS[2], ARGV[i], ARGV[i + 1]) end "
                    + "return 1",
            Long.class);

    private final UnreadSnapshotLoader snapshotLoader;
    private final StringRedisTemplate redisTemplate;

    public UnreadCountResponseDto rebuild(Long accountId) {
        return rebuild(accountId, currentGeneration(accountId)).snapshot();
    }

    public RebuildResult rebuild(Long accountId, long expectedGeneration) {
        UnreadSnapshotLoader.UnreadSnapshot snapshot = snapshotLoader.load(accountId);
        boolean applied = replaceIfCurrent(accountId, expectedGeneration, snapshot);
        return new RebuildResult(
                UnreadCountResponseDto.of(snapshot.total(), snapshot.categoryCounts()),
                applied);
    }

    private long currentGeneration(Long accountId) {
        String key = UnreadCacheKeys.generation(accountId);

        while (true) {
            String value = redisTemplate.opsForValue().get(key);

            if (value == null) {
                return 0L;
            }

            try {
                return Long.parseLong(value);

            } catch (NumberFormatException e) {
                Long deleted = redisTemplate.execute(
                        DELETE_IF_VALUE_MATCHES,
                        List.of(key),
                        value
                );

                if (deleted != null && deleted == 1L) {
                    log.warn(
                            "unread 캐시 세대 형식 오류 — 손상된 키 제거: accountId={}, value={}",
                            accountId,
                            value
                    );
                }

                // 삭제 여부와 관계없이 다시 읽는다.
                // 그 사이 다른 요청이 정상 generation을 만들었을 수 있다.
            }
        }
    }

    private boolean replaceIfCurrent(
            Long accountId,
            long expectedGeneration,
            UnreadSnapshotLoader.UnreadSnapshot snapshot
    ) {
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(expectedGeneration));
        args.add(String.valueOf(snapshot.total()));
        snapshot.categoryCounts().forEach((NotificationCategory category, Long count) -> {
            args.add(category.name());
            args.add(String.valueOf(count));
        });
        Long applied = redisTemplate.execute(
                REPLACE_IF_CURRENT_GENERATION,
                List.of(
                        UnreadCacheKeys.total(accountId),
                        UnreadCacheKeys.category(accountId),
                        UnreadCacheKeys.generation(accountId)),
                args.toArray());
        return applied != null && applied == 1L;
    }

    public record RebuildResult(UnreadCountResponseDto snapshot, boolean applied) {
    }
}
