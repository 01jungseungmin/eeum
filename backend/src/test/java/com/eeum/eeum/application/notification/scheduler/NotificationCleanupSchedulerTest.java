package com.eeum.eeum.application.notification.scheduler;

import com.eeum.eeum.application.notification.service.UnreadCountService;
import com.eeum.eeum.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCleanupSchedulerTest {

    @InjectMocks
    private NotificationCleanupScheduler scheduler;

    @Mock private NotificationRepository notificationRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private UnreadCountService unreadCountService;

    @SuppressWarnings("unchecked")
    @Mock private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @SuppressWarnings("unchecked")
    private void stubScan(List<String> keys) {
        Cursor<String> cursor = org.mockito.Mockito.mock(Cursor.class);
        var iterator = keys.iterator();
        lenient().when(cursor.hasNext()).thenAnswer(invocation -> iterator.hasNext());
        lenient().when(cursor.next()).thenAnswer(invocation -> iterator.next());
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
    }

    // KEYS는 매칭이 끝날 때까지 Redis 전체를 블로킹한다 — 그동안 이 서버의 모든
    // Redis 명령(세션 검증·분산 락·캐시)이 함께 멈춘다.
    @Test
    void 키_순회는_KEYS가_아니라_SCAN으로_한다() {
        // given
        stubScan(List.of());

        // when
        scheduler.recalculateUnreadCounts();

        // then
        verify(redisTemplate).scan(any(ScanOptions.class));
        verify(redisTemplate, never()).keys(anyString());
    }

    // 재계산은 계정 행 비관적 락을 잡는다. 전 계정에 대해 무조건 돌리면
    // 5분마다 전 계정 행을 차례로 잠그게 된다(자원 예산 문서 금지 패턴 7).
    @Test
    void 캐시값과_DB값이_같으면_재계산하지_않는다() {
        // given
        stubScan(List.of("unread:account:1", "unread:account:2"));
        when(notificationRepository.countUnreadByAccountIds(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, 3L, 2L, 0L));
        when(valueOps.multiGet(List.of("unread:account:1", "unread:account:2")))
                .thenReturn(List.of("3", "0"));

        // when
        scheduler.recalculateUnreadCounts();

        // then
        verify(unreadCountService, never()).refreshFromDb(anyLong());
    }

    @Test
    void 불일치한_계정만_재계산한다() {
        // given
        stubScan(List.of("unread:account:1", "unread:account:2"));
        when(notificationRepository.countUnreadByAccountIds(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, 3L, 2L, 5L));
        when(valueOps.multiGet(List.of("unread:account:1", "unread:account:2")))
                .thenReturn(List.of("3", "99"));

        // when
        scheduler.recalculateUnreadCounts();

        // then
        verify(unreadCountService).refreshFromDb(2L);
        verify(unreadCountService, never()).refreshFromDb(1L);
    }

    // DB에 미읽음이 없으면 집계 결과에 그 계정이 없다 — 0으로 봐야 한다.
    // 없다고 건너뛰면 "캐시엔 5, DB엔 0"인 계정이 영원히 보정되지 않는다.
    @Test
    void 집계_결과에_없는_계정은_0으로_보고_보정한다() {
        // given
        stubScan(List.of("unread:account:7"));
        when(notificationRepository.countUnreadByAccountIds(List.of(7L))).thenReturn(Map.of());
        when(valueOps.multiGet(List.of("unread:account:7"))).thenReturn(List.of("5"));

        // when
        scheduler.recalculateUnreadCounts();

        // then
        verify(unreadCountService).refreshFromDb(7L);
    }

    @Test
    void 스캔_이후_키가_사라진_계정은_건너뛴다() {
        // given: multiGet은 없는 키 자리에 null을 돌려준다
        stubScan(List.of("unread:account:1"));
        when(notificationRepository.countUnreadByAccountIds(List.of(1L))).thenReturn(Map.of(1L, 3L));
        when(valueOps.multiGet(List.of("unread:account:1")))
                .thenReturn(java.util.Collections.singletonList(null));

        // when
        scheduler.recalculateUnreadCounts();

        // then: 캐시가 이미 없으므로 다음 조회가 DB에서 복구한다
        verify(unreadCountService, never()).refreshFromDb(anyLong());
    }

    @Test
    void 캐시값이_깨졌으면_무효화해_다음_조회가_DB에서_복구하게_한다() {
        // given
        stubScan(List.of("unread:account:1"));
        when(notificationRepository.countUnreadByAccountIds(List.of(1L))).thenReturn(Map.of(1L, 3L));
        when(valueOps.multiGet(List.of("unread:account:1"))).thenReturn(List.of("not-a-number"));

        // when
        scheduler.recalculateUnreadCounts();

        // then
        verify(unreadCountService).invalidateSnapshot(1L);
        verify(unreadCountService, never()).refreshFromDb(anyLong());
    }

    @Test
    void 형식이_어긋난_키는_집계_대상에서_제외한다() {
        // given
        stubScan(List.of("unread:account:abc"));

        // when
        scheduler.recalculateUnreadCounts();

        // then: 파싱 실패 하나가 나머지 보정을 막지 않고, DB 집계도 호출되지 않는다
        verify(notificationRepository, never()).countUnreadByAccountIds(anyCollection());
    }
}
