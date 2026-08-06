package com.eeum.eeum.application.chat.scheduler;

import com.eeum.eeum.application.chat.service.ChatMessageService;
import com.eeum.eeum.application.chat.service.ChatUnreadService;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatUnreadReconcileSchedulerTest {

    @InjectMocks
    private ChatUnreadReconcileScheduler scheduler;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatMessageService chatMessageService;
    @Mock private ChatUnreadService chatUnreadService;

    @SuppressWarnings("unchecked")
    @Mock private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // SCAN 결과를 흉내내는 커서
    @SuppressWarnings("unchecked")
    private void stubScan(List<String> keys) {
        Cursor<String> cursor = mock(keys);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
    }

    @SuppressWarnings("unchecked")
    private Cursor<String> mock(List<String> keys) {
        Cursor<String> cursor = org.mockito.Mockito.mock(Cursor.class);
        var it = keys.iterator();
        lenient().when(cursor.hasNext()).thenAnswer(inv -> it.hasNext());
        lenient().when(cursor.next()).thenAnswer(inv -> it.next());
        return cursor;
    }

    // ===================== 전체 카운트 보정 =====================

    @Test
    void 캐시값과_DB값이_다르면_CAS로_보정한다() {
        // Given
        stubScan(List.of("unread:chat:1"));
        when(valueOps.get("unread:chat:1")).thenReturn("5");
        when(chatMessageService.sumUnreadFromDb(1L)).thenReturn(3L);
        when(chatUnreadService.compareAndSetTotal(1L, 5L, 3L)).thenReturn(true);

        // When
        scheduler.reconcileChatUnread();

        // Then
        verify(chatUnreadService).compareAndSetTotal(1L, 5L, 3L);
    }

    @Test
    void 조회_사이에_값이_바뀌면_덮어쓰지_않는다() {
        // Given: CAS 실패 = "DB 조회 → SET" 사이에 메시지가 도착해 INCR된 상황.
        //        무조건 SET하면 그 증가분이 사라지므로 건너뛰고 다음 주기에 다시 맞춘다.
        stubScan(List.of("unread:chat:1"));
        when(valueOps.get("unread:chat:1")).thenReturn("5");
        when(chatMessageService.sumUnreadFromDb(1L)).thenReturn(3L);
        when(chatUnreadService.compareAndSetTotal(1L, 5L, 3L)).thenReturn(false);

        // When
        scheduler.reconcileChatUnread();

        // Then: 강제 SET 경로가 없어야 한다
        verify(valueOps, never()).set(any(), any());
    }

    @Test
    void 캐시값과_DB값이_같으면_아무것도_하지_않는다() {
        // Given
        stubScan(List.of("unread:chat:1"));
        when(valueOps.get("unread:chat:1")).thenReturn("3");
        when(chatMessageService.sumUnreadFromDb(1L)).thenReturn(3L);

        // When
        scheduler.reconcileChatUnread();

        // Then
        verify(chatUnreadService, never()).compareAndSetTotal(anyLong(), any(), anyLong());
    }

    @Test
    void 캐시가_없으면_기대값_null로_CAS한다() {
        // Given: 키가 없는 상태에서 다른 요청이 먼저 만들면 CAS가 실패해야 한다
        stubScan(List.of("unread:chat:1"));
        when(valueOps.get("unread:chat:1")).thenReturn(null);
        when(chatMessageService.sumUnreadFromDb(1L)).thenReturn(2L);
        when(chatUnreadService.compareAndSetTotal(1L, null, 2L)).thenReturn(true);

        // When
        scheduler.reconcileChatUnread();

        // Then
        verify(chatUnreadService).compareAndSetTotal(1L, null, 2L);
    }

    // ===================== 종료된 방 키 정리 =====================

    @Test
    void 종료된_방의_방별_키만_삭제한다() {
        // Given: 10번 방은 종료, 20번 방은 활성
        stubScan(List.of("unread:chat:1:room:10", "unread:chat:1:room:20"));
        when(chatRoomRepository.findClosedRoomIdsIn(any())).thenReturn(List.of(10L));

        // When
        scheduler.reconcileChatUnread();

        // Then: 활성 방 키는 건드리지 않는다
        verify(redisTemplate).delete(eq(List.of("unread:chat:1:room:10")));
    }

    @Test
    void 종료된_방이_없으면_아무_키도_삭제하지_않는다() {
        // Given
        stubScan(List.of("unread:chat:1:room:20"));
        when(chatRoomRepository.findClosedRoomIdsIn(any())).thenReturn(List.of());

        // When
        scheduler.reconcileChatUnread();

        // Then
        verify(redisTemplate, never()).delete(any(List.class));
    }
}
