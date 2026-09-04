package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.response.UnreadCountResponseDto;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnreadSnapshotRebuilderTest {

    private static final Long ACCOUNT_ID = 6L;

    @InjectMocks
    private UnreadSnapshotRebuilder snapshotRebuilder;

    @Mock
    private UnreadSnapshotLoader snapshotLoader;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void 손상된_generation은_비교삭제한_뒤_새_스냅샷을_반영한다() {
        // 첫 조회에서는 손상된 generation을 읽는다.
        // compare-and-delete 성공 후 다시 조회하면 키가 없으므로 generation=0으로 재구축한다.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("unread:generation:6"))
                .thenReturn("broken", null);

        when(snapshotLoader.load(ACCOUNT_ID))
                .thenReturn(new UnreadSnapshotLoader.UnreadSnapshot(
                        2L,
                        new EnumMap<>(NotificationCategory.class)
                ));

        // 1번째 execute: 손상 generation compare-and-delete
        // 2번째 execute: 새 snapshot CAS 반영
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                any(List.class),
                any(Object[].class)
        )).thenReturn(1L, 1L);

        UnreadCountResponseDto result =
                snapshotRebuilder.rebuild(ACCOUNT_ID);

        assertThat(result.getUnreadCount()).isEqualTo(2L);

        // 이제 직접 delete()하지 않고 Lua compare-and-delete를 사용한다.
        verify(redisTemplate, never())
                .delete("unread:generation:6");

        verify(redisTemplate, times(2))
                .execute(
                        any(DefaultRedisScript.class),
                        any(List.class),
                        any(Object[].class)
                );
    }

    @Test
    void 손상된_generation을_읽은_뒤_새_generation이_생기면_삭제하지_않고_다시_읽는다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // A가 broken을 읽은 뒤,
        // 다른 요청 B가 generation을 1로 갱신했다고 가정한다.
        when(valueOperations.get("unread:generation:6"))
                .thenReturn("broken", "1");

        when(snapshotLoader.load(ACCOUNT_ID))
                .thenReturn(new UnreadSnapshotLoader.UnreadSnapshot(
                        2L,
                        new EnumMap<>(NotificationCategory.class)
                ));

        // compare-and-delete는 현재 값이 이미 "1"이므로 실패(0)
        // 이후 generation=1을 다시 읽고 snapshot CAS는 성공(1)
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                any(List.class),
                any(Object[].class)
        )).thenReturn(0L, 1L);

        UnreadCountResponseDto result =
                snapshotRebuilder.rebuild(ACCOUNT_ID);

        assertThat(result.getUnreadCount()).isEqualTo(2L);

        verify(redisTemplate, never())
                .delete("unread:generation:6");

        ArgumentCaptor<Object[]> argsCaptor =
                ArgumentCaptor.forClass(Object[].class);

        verify(redisTemplate, times(2))
                .execute(
                        any(DefaultRedisScript.class),
                        any(List.class),
                        argsCaptor.capture()
                );

        List<Object[]> executions = argsCaptor.getAllValues();

        // 첫 Lua: "broken"일 때만 삭제 시도
        assertThat(executions.get(0))
                .containsExactly("broken");

        // 두 번째 Lua: 다시 읽은 최신 generation=1을 기준으로 snapshot 반영
        assertThat(executions.get(1)[0])
                .isEqualTo("1");
    }

    @Test
    void 현재_세대와_일치할_때만_DB_스냅샷을_캐시에_반영한다() {
        EnumMap<NotificationCategory, Long> categories =
                new EnumMap<>(NotificationCategory.class);

        for (NotificationCategory category : NotificationCategory.values()) {
            categories.put(category, 0L);
        }

        categories.put(NotificationCategory.ORDER, 3L);

        when(snapshotLoader.load(ACCOUNT_ID))
                .thenReturn(new UnreadSnapshotLoader.UnreadSnapshot(
                        3L,
                        categories
                ));

        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                any(List.class),
                any(Object[].class)
        )).thenReturn(1L);

        UnreadSnapshotRebuilder.RebuildResult result =
                snapshotRebuilder.rebuild(ACCOUNT_ID, 4L);

        assertThat(result.applied()).isTrue();
        assertThat(result.snapshot().getUnreadCount()).isEqualTo(3L);

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of(
                        "unread:account:6",
                        "unread:category:6",
                        "unread:generation:6"
                )),
                any(Object[].class)
        );
    }

    @Test
    void 새_무효화가_먼저_일어나면_오래된_스냅샷을_전송하지_않는다() {
        when(snapshotLoader.load(ACCOUNT_ID))
                .thenReturn(new UnreadSnapshotLoader.UnreadSnapshot(
                        2L,
                        new EnumMap<>(NotificationCategory.class)
                ));

        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                any(List.class),
                any(Object[].class)
        )).thenReturn(0L);

        UnreadSnapshotRebuilder.RebuildResult result =
                snapshotRebuilder.rebuild(ACCOUNT_ID, 1L);

        assertThat(result.applied()).isFalse();
    }
}