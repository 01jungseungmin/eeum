package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnreadSnapshotRebuilderTest {

    private static final Long ACCOUNT_ID = 6L;

    @InjectMocks private UnreadSnapshotRebuilder snapshotRebuilder;
    @Mock private UnreadSnapshotLoader snapshotLoader;
    @Mock private StringRedisTemplate redisTemplate;

    @Test
    void 현재_세대와_일치할_때만_DB_스냅샷을_캐시에_반영한다() {
        EnumMap<NotificationCategory, Long> categories = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory category : NotificationCategory.values()) {
            categories.put(category, 0L);
        }
        categories.put(NotificationCategory.ORDER, 3L);
        when(snapshotLoader.load(ACCOUNT_ID)).thenReturn(new UnreadSnapshotLoader.UnreadSnapshot(3L, categories));
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(1L);

        UnreadSnapshotRebuilder.RebuildResult result = snapshotRebuilder.rebuild(ACCOUNT_ID, 4L);

        assertThat(result.applied()).isTrue();
        assertThat(result.snapshot().getUnreadCount()).isEqualTo(3L);
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("unread:account:6", "unread:category:6", "unread:generation:6")),
                any(Object[].class));
    }

    @Test
    void 새_무효화가_먼저_일어나면_오래된_스냅샷을_전송하지_않는다() {
        when(snapshotLoader.load(ACCOUNT_ID)).thenReturn(new UnreadSnapshotLoader.UnreadSnapshot(2L, new EnumMap<>(NotificationCategory.class)));
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(0L);

        UnreadSnapshotRebuilder.RebuildResult result = snapshotRebuilder.rebuild(ACCOUNT_ID, 1L);

        assertThat(result.applied()).isFalse();
    }
}
