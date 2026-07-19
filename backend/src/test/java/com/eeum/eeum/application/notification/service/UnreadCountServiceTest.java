package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.response.UnreadCountResponseDto;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnreadCountServiceTest {

    @InjectMocks
    private UnreadCountService unreadCountService;

    @Mock private NotificationRepository notificationRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private HashOperations<String, Object, Object> hashOperations;

    private static final Long ACCOUNT_ID = 6L;
    private static final String TOTAL_KEY = "unread:account:" + ACCOUNT_ID;
    private static final String CATEGORY_KEY = "unread:category:" + ACCOUNT_ID;

    private void stubRedis() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void 캐시_히트시_DB_집계_없이_Redis_값으로_응답한다() {
        // given
        stubRedis();
        when(valueOperations.get(TOTAL_KEY)).thenReturn("34");
        when(hashOperations.entries(CATEGORY_KEY)).thenReturn(Map.of("ORDER", "1", "CHAT", "22"));

        // when
        UnreadCountResponseDto result = unreadCountService.getUnreadCount(ACCOUNT_ID);

        // then: 전체/카테고리 모두 DB 조회 없이 캐시로 응답, 누락 카테고리는 0으로 채움
        assertThat(result.getUnreadCount()).isEqualTo(34);
        assertThat(result.getByCategory().get(NotificationCategory.ORDER)).isEqualTo(1);
        assertThat(result.getByCategory().get(NotificationCategory.CHAT)).isEqualTo(22);
        assertThat(result.getByCategory().get(NotificationCategory.REVIEW)).isZero();
        assertThat(result.getByCategory()).hasSize(NotificationCategory.values().length);
        verify(notificationRepository, never()).countByAccount_AccountIdAndIsReadFalse(anyLong());
        verify(notificationRepository, never()).countUnreadByCategory(anyLong());
    }

    @Test
    void 캐시_미스시_DB에서_집계해_Redis에_복구한다() {
        // given
        stubRedis();
        when(valueOperations.get(TOTAL_KEY)).thenReturn(null);
        when(hashOperations.entries(CATEGORY_KEY)).thenReturn(Map.of());
        when(notificationRepository.countByAccount_AccountIdAndIsReadFalse(ACCOUNT_ID)).thenReturn(3L);
        when(notificationRepository.countUnreadByCategory(ACCOUNT_ID))
                .thenReturn(Map.of(NotificationCategory.ORDER, 3L));

        // when
        UnreadCountResponseDto result = unreadCountService.getUnreadCount(ACCOUNT_ID);

        // then
        assertThat(result.getUnreadCount()).isEqualTo(3);
        assertThat(result.getByCategory().get(NotificationCategory.ORDER)).isEqualTo(3);
        verify(valueOperations).set(TOTAL_KEY, "3");
        verify(hashOperations).putAll(anyString(), anyMap());
    }

    @Test
    void 카운트_변경시_카테고리_캐시가_무효화된다() {
        // given
        stubRedis();

        // when
        unreadCountService.increment(ACCOUNT_ID);

        // then
        verify(valueOperations).increment(TOTAL_KEY);
        verify(redisTemplate).delete(CATEGORY_KEY);
    }
}
