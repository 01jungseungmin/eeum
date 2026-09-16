package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.response.UnreadCountResponseDto;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnreadCountServiceTest {

    @InjectMocks
    private UnreadCountService unreadCountService;

    @Mock private UnreadSnapshotRebuilder snapshotRebuilder;
    @Mock private StringRedisTemplate redisTemplate;

    private static final Long ACCOUNT_ID = 6L;
    private static final String TOTAL_KEY = "unread:account:" + ACCOUNT_ID;
    private static final String CATEGORY_KEY = "unread:category:" + ACCOUNT_ID;

    private List<String> completeSnapshot(long total, long order, long chat) {
        List<String> snapshot = new java.util.ArrayList<>();
        snapshot.add(String.valueOf(total));
        for (NotificationCategory category : NotificationCategory.values()) {
            long count = category == NotificationCategory.ORDER ? order
                    : category == NotificationCategory.CHAT ? chat : 0L;
            snapshot.add(category.name());
            snapshot.add(String.valueOf(count));
        }
        return snapshot;
    }

    private UnreadCountResponseDto snapshotDto(long total) {
        Map<NotificationCategory, Long> byCategory = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory category : NotificationCategory.values()) {
            byCategory.put(category, category == NotificationCategory.ORDER ? total : 0L);
        }
        return UnreadCountResponseDto.of(total, byCategory);
    }

    private void stubCache(List<String> snapshot) {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(List.of(TOTAL_KEY, CATEGORY_KEY))))
                .thenReturn(snapshot);
    }

    @Test
    void 캐시_히트시_DB_재계산_없이_Redis_값으로_응답한다() {
        // given
        stubCache(completeSnapshot(34, 1, 22));

        // when
        UnreadCountResponseDto result = unreadCountService.getUnreadCount(ACCOUNT_ID);

        // then: 완성된 전체/카테고리 캐시는 DB를 만지지 않고 그대로 응답
        assertThat(result.getUnreadCount()).isEqualTo(34);
        assertThat(result.getByCategory().get(NotificationCategory.ORDER)).isEqualTo(1);
        assertThat(result.getByCategory().get(NotificationCategory.CHAT)).isEqualTo(22);
        assertThat(result.getByCategory().get(NotificationCategory.REVIEW)).isZero();
        assertThat(result.getByCategory()).hasSize(NotificationCategory.values().length);
        verify(snapshotRebuilder, never()).rebuild(anyLong());
    }

    // 캐시 히트 경로가 트랜잭션을 여는 유일한 협력자(Rebuilder)를 부르지 않는다는 것이
    // "Redis만으로 끝나 DB 커넥션을 잡지 않는다"의 구조적 표현이다.
    @Test
    void 캐시_히트_경로는_트랜잭션_경계를_가진_협력자를_호출하지_않는다() {
        // given
        stubCache(completeSnapshot(7, 7, 0));

        // when
        unreadCountService.getUnreadCount(ACCOUNT_ID);

        // then
        verify(snapshotRebuilder, never()).rebuild(anyLong());
    }

    @Test
    void 카테고리가_일부만_있는_캐시는_미완성으로_보고_DB에서_복구한다() {
        // given: 전체 값은 있지만 카테고리 해시가 비어 있다
        stubCache(List.of("34"));
        when(snapshotRebuilder.rebuild(ACCOUNT_ID)).thenReturn(snapshotDto(3));

        // when
        UnreadCountResponseDto result = unreadCountService.getUnreadCount(ACCOUNT_ID);

        // then
        assertThat(result.getUnreadCount()).isEqualTo(3);
        verify(snapshotRebuilder).rebuild(ACCOUNT_ID);
    }

    @Test
    void 캐시_미스시_DB에서_집계해_복구한다() {
        // given
        stubCache(List.of());
        when(snapshotRebuilder.rebuild(ACCOUNT_ID)).thenReturn(snapshotDto(3));

        // when
        UnreadCountResponseDto result = unreadCountService.getUnreadCount(ACCOUNT_ID);

        // then
        assertThat(result.getUnreadCount()).isEqualTo(3);
        assertThat(result.getByCategory().get(NotificationCategory.ORDER)).isEqualTo(3);
        verify(snapshotRebuilder).rebuild(ACCOUNT_ID);
    }

    @Test
    void 카운트_변경은_INCR이_아닌_DB_스냅샷_재작성으로_처리한다() {
        // when
        unreadCountService.refreshFromDb(ACCOUNT_ID);

        // then: Redis 카운터를 직접 증감하지 않는다 —
        // 증감과 DB 스냅샷 SET을 섞으면 실행 순서가 뒤집힐 때 캐시가 틀어진다
        verify(snapshotRebuilder).rebuild(ACCOUNT_ID);
        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).opsForHash();
    }

    @Test
    void 무효화는_전체와_카테고리_키를_함께_지운다() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class))).thenReturn(1L);

        // when
        unreadCountService.invalidateSnapshot(ACCOUNT_ID);

        // then: 세대를 올리고 두 캐시 키를 원자적으로 비운다
        verify(redisTemplate).execute(any(DefaultRedisScript.class),
                eq(List.of(TOTAL_KEY, CATEGORY_KEY, "unread:generation:" + ACCOUNT_ID)));
    }

    @Test
    void 여러_계정_무효화는_키를_한_번에_넘긴다() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class))).thenReturn(1L, 2L);

        // when
        unreadCountService.invalidateSnapshots(List.of(6L, 7L));

        // then: 각 계정의 세대를 올려 이전 비동기 결과를 무효화한다
        verify(redisTemplate).execute(any(DefaultRedisScript.class),
                eq(List.of("unread:account:6", "unread:category:6", "unread:generation:6")));
        verify(redisTemplate).execute(any(DefaultRedisScript.class),
                eq(List.of("unread:account:7", "unread:category:7", "unread:generation:7")));
    }

    @Test
    void 무효화_대상이_비어_있으면_Redis를_호출하지_않는다() {
        // when
        unreadCountService.invalidateSnapshots(List.of());

        // then
        verify(redisTemplate, never()).execute(any(DefaultRedisScript.class), any(List.class));
    }
}
