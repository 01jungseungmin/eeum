package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.ChatRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatUnreadServiceTest {

    @InjectMocks
    private ChatUnreadService chatUnreadService;

    @Mock private StringRedisTemplate redisTemplate;
    @SuppressWarnings("unchecked")
    @Mock private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        // resetRoom 테스트는 opsForValue()를 사용하지 않으므로 lenient로 처리
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ===================== increment =====================

    @Test
    void unread_증가_전체_및_방별_카운트_모두_증가() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;

        // When
        chatUnreadService.increment(accountId, roomId);

        // Then
        verify(valueOps).increment(ChatRedisKeys.totalUnread(accountId));
        verify(valueOps).increment(ChatRedisKeys.roomUnread(accountId, roomId));
    }

    // ===================== resetRoom (Lua 스크립트 기반) =====================
    // resetRoom은 원자성 보장을 위해 Lua 스크립트로 구현되어 있다.
    // 단위 테스트에서는 스크립트의 내부 Redis 조작은 검증할 수 없으므로,
    // redisTemplate.execute(script, keys)가 올바른 키 목록과 함께 호출됨만 검증한다.

    @Test
    void resetRoom_Lua스크립트_실행됨_올바른_roomKey와_accountKey_전달() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        String expectedRoomKey = ChatRedisKeys.roomUnread(accountId, roomId);
        String expectedAccountKey = ChatRedisKeys.totalUnread(accountId);

        // When
        chatUnreadService.resetRoom(accountId, roomId);

        // Then — Lua 스크립트는 execute(script, List<keys>) 형태로 호출됨
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture());
        assertThat(keysCaptor.getValue())
                .containsExactly(expectedRoomKey, expectedAccountKey);
    }

    @Test
    void resetRoom_다른_accountId와_roomId로_호출하면_각기_다른_키_전달() {
        // Given
        Long accountId = 5L;
        Long roomId = 99L;
        String expectedRoomKey = ChatRedisKeys.roomUnread(accountId, roomId);
        String expectedAccountKey = ChatRedisKeys.totalUnread(accountId);

        // When
        chatUnreadService.resetRoom(accountId, roomId);

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture());
        assertThat(keysCaptor.getValue())
                .containsExactly(expectedRoomKey, expectedAccountKey);
    }

    // ===================== getTotalUnread =====================

    @Test
    void 전체_unread_캐시히트_DB폴백_미호출() {
        // Given
        Long accountId = 1L;
        String accountKey = ChatRedisKeys.totalUnread(accountId);
        when(valueOps.get(accountKey)).thenReturn("7");

        // When
        long result = chatUnreadService.getTotalUnread(accountId, () -> {
            throw new AssertionError("DB fallback 호출 금지");
        });

        // Then
        assertThat(result).isEqualTo(7L);
        verify(valueOps, never()).setIfAbsent(anyString(), anyString());
    }

    @Test
    void 전체_unread_캐시미스_DB폴백_후_Redis에_복구() {
        // Given
        Long accountId = 1L;
        String accountKey = ChatRedisKeys.totalUnread(accountId);
        when(valueOps.get(accountKey)).thenReturn(null);

        // When
        long result = chatUnreadService.getTotalUnread(accountId, () -> 4L);

        // Then
        assertThat(result).isEqualTo(4L);
        verify(valueOps).setIfAbsent(accountKey, "4");
    }

    @Test
    void 전체_unread_캐시값이_파싱_불가능하면_0_반환() {
        // Given
        Long accountId = 1L;
        String accountKey = ChatRedisKeys.totalUnread(accountId);
        when(valueOps.get(accountKey)).thenReturn("corrupted");

        // When
        long result = chatUnreadService.getTotalUnread(accountId, () -> 99L);

        // Then
        assertThat(result).isEqualTo(0L);
    }

    // ===================== getRoomUnread =====================

    @Test
    void 방별_unread_캐시히트_DB폴백_미호출() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        String roomKey = ChatRedisKeys.roomUnread(accountId, roomId);
        when(valueOps.get(roomKey)).thenReturn("2");

        // When
        long result = chatUnreadService.getRoomUnread(accountId, roomId, () -> {
            throw new AssertionError("DB fallback 호출 금지");
        });

        // Then
        assertThat(result).isEqualTo(2L);
        verify(valueOps, never()).setIfAbsent(anyString(), anyString());
    }

    @Test
    void 방별_unread_캐시미스_DB폴백_후_Redis에_복구() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        String roomKey = ChatRedisKeys.roomUnread(accountId, roomId);
        when(valueOps.get(roomKey)).thenReturn(null);

        // When
        long result = chatUnreadService.getRoomUnread(accountId, roomId, () -> 6L);

        // Then
        assertThat(result).isEqualTo(6L);
        verify(valueOps).setIfAbsent(roomKey, "6");
    }
}
