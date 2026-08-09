package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.response.UnreadCountResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnreadCountServiceTest {

    @InjectMocks
    private UnreadCountService unreadCountService;

    @Mock private NotificationRepository notificationRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

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

    private void stubAccountLock() {
        when(accountRepository.findByIdWithLock(ACCOUNT_ID))
                .thenReturn(java.util.Optional.of(mock(Account.class)));
    }

    @Test
    void 캐시_히트시_DB_집계_없이_Redis_값으로_응답한다() {
        // given
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                org.mockito.ArgumentMatchers.eq(List.of(TOTAL_KEY, CATEGORY_KEY)),
                any(Object[].class)))
                .thenReturn(completeSnapshot(34, 1, 22));

        // when
        UnreadCountResponseDto result = unreadCountService.getUnreadCount(ACCOUNT_ID);

        // then: 완성된 전체/카테고리 캐시는 DB 조회 없이 그대로 응답
        assertThat(result.getUnreadCount()).isEqualTo(34);
        assertThat(result.getByCategory().get(NotificationCategory.ORDER)).isEqualTo(1);
        assertThat(result.getByCategory().get(NotificationCategory.CHAT)).isEqualTo(22);
        assertThat(result.getByCategory().get(NotificationCategory.REVIEW)).isZero();
        assertThat(result.getByCategory()).hasSize(NotificationCategory.values().length);
        verify(notificationRepository, never()).countByAccount_AccountIdAndIsReadFalse(anyLong());
        verify(notificationRepository, never()).countUnreadByCategory(anyLong());
        verify(accountRepository, never()).findByIdWithLock(anyLong());
        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).opsForHash();
    }

    @Test
    void 캐시_미스시_DB에서_집계해_Redis에_복구한다() {
        // given
        stubAccountLock();
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                org.mockito.ArgumentMatchers.eq(List.of(TOTAL_KEY, CATEGORY_KEY)),
                any(Object[].class)))
                .thenReturn(List.of());
        when(notificationRepository.countByAccount_AccountIdAndIsReadFalse(ACCOUNT_ID)).thenReturn(3L);
        when(notificationRepository.countUnreadByCategory(ACCOUNT_ID))
                .thenReturn(Map.of(NotificationCategory.ORDER, 3L));

        // when
        UnreadCountResponseDto result = unreadCountService.getUnreadCount(ACCOUNT_ID);

        // then
        assertThat(result.getUnreadCount()).isEqualTo(3);
        assertThat(result.getByCategory().get(NotificationCategory.ORDER)).isEqualTo(3);
        verify(redisTemplate, times(2)).execute(
                any(DefaultRedisScript.class),
                org.mockito.ArgumentMatchers.eq(List.of(TOTAL_KEY, CATEGORY_KEY)),
                any(Object[].class));
        verify(accountRepository).findByIdWithLock(ACCOUNT_ID);
    }

    @Test
    void 카운트_변경은_INCR이_아닌_Account락과_DB_스냅샷으로_전체_캐시를_재작성한다() {
        // given
        stubAccountLock();
        when(notificationRepository.countByAccount_AccountIdAndIsReadFalse(ACCOUNT_ID)).thenReturn(5L);
        when(notificationRepository.countUnreadByCategory(ACCOUNT_ID))
                .thenReturn(Map.of(NotificationCategory.REVIEW, 5L));

        // when
        unreadCountService.increment(ACCOUNT_ID);

        // then
        verify(accountRepository).findByIdWithLock(ACCOUNT_ID);
        verify(valueOperations, never()).increment(TOTAL_KEY);
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                org.mockito.ArgumentMatchers.eq(List.of(TOTAL_KEY, CATEGORY_KEY)),
                any(Object[].class));
    }
}
