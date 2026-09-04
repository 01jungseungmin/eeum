package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.application.notification.dto.response.UnreadCountResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.notification.enums.NotificationCategory;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnreadSnapshotRebuilderTest {

    @InjectMocks
    private UnreadSnapshotRebuilder snapshotRebuilder;

    @Mock private NotificationRepository notificationRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private StringRedisTemplate redisTemplate;

    private static final Long ACCOUNT_ID = 6L;
    private static final String TOTAL_KEY = "unread:account:" + ACCOUNT_ID;
    private static final String CATEGORY_KEY = "unread:category:" + ACCOUNT_ID;

    private void stubAccountLock() {
        when(accountRepository.findByIdWithLock(ACCOUNT_ID))
                .thenReturn(Optional.of(mock(Account.class)));
    }

    @Test
    void 계정_행을_잠근_뒤_DB_스냅샷으로_전체_캐시를_재작성한다() {
        // given
        stubAccountLock();
        when(notificationRepository.countByAccount_AccountIdAndIsReadFalse(ACCOUNT_ID)).thenReturn(5L);
        when(notificationRepository.countUnreadByCategory(ACCOUNT_ID))
                .thenReturn(Map.of(NotificationCategory.REVIEW, 5L));

        // when
        UnreadCountResponseDto result = snapshotRebuilder.rebuild(ACCOUNT_ID);

        // then: 읽기와 쓰기가 같은 mutex 안에 있어야 두 재계산이 겹쳐도 낡은 값이 남지 않는다
        verify(accountRepository).findByIdWithLock(ACCOUNT_ID);
        assertThat(result.getUnreadCount()).isEqualTo(5);
        assertThat(result.getByCategory().get(NotificationCategory.REVIEW)).isEqualTo(5);
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of(TOTAL_KEY, CATEGORY_KEY)),
                any(Object[].class));
        // Redis 카운터 증감이 아니라 스냅샷 교체다
        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).opsForHash();
    }

    @Test
    void DB에_없는_카테고리도_0으로_채워_완성된_캐시를_만든다() {
        // given
        stubAccountLock();
        when(notificationRepository.countByAccount_AccountIdAndIsReadFalse(ACCOUNT_ID)).thenReturn(2L);
        when(notificationRepository.countUnreadByCategory(ACCOUNT_ID))
                .thenReturn(Map.of(NotificationCategory.ORDER, 2L));

        // when
        UnreadCountResponseDto result = snapshotRebuilder.rebuild(ACCOUNT_ID);

        // then: 카테고리가 하나라도 빠지면 조회가 그 캐시를 미완성으로 보고 매번 DB로 되돌아간다
        assertThat(result.getByCategory()).hasSize(NotificationCategory.values().length);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of(TOTAL_KEY, CATEGORY_KEY)),
                args.capture());
        // total 1개 + (카테고리명, 값) 쌍
        assertThat(args.getValue())
                .hasSize(1 + NotificationCategory.values().length * 2);
    }

    @Test
    void 없는_계정이면_캐시를_건드리지_않고_예외를_던진다() {
        // given
        when(accountRepository.findByIdWithLock(ACCOUNT_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> snapshotRebuilder.rebuild(ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_FOUND);
        verify(redisTemplate, never()).execute(any(DefaultRedisScript.class), any(), any(Object[].class));
    }
}
