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
import static org.mockito.ArgumentMatchers.eq;
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
    @SuppressWarnings("unchecked")
    void unread_증가_전체_및_방별_카운트_모두_증가() {
        // Given: 두 키를 개별 INCR하면 그 사이 resetRoom이 끼어들어 전체 배지가 영구히 부풀 수 있어
        //        하나의 Lua 스크립트로 원자 처리한다
        Long accountId = 1L;
        Long roomId = 10L;

        // When
        chatUnreadService.increment(accountId, roomId);

        // Then: 방별 키와 전체 키를 함께 넘긴 단일 스크립트 실행
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture());
        assertThat(keysCaptor.getValue())
                .containsExactly("unread:chat:1:room:10", "unread:chat:1");
        verify(valueOps, never()).increment(anyString());
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

    // ===================== compareAndSetTotal =====================

    @Test
    @SuppressWarnings("unchecked")
    void 전체_unread_CAS_성공이면_true를_반환하고_기대값과_새값을_전달한다() {
        // Given
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("unread:chat:1")),
                eq("5"),
                eq("3")))
                .thenReturn(1L);

        // When
        boolean updated = chatUnreadService.compareAndSetTotal(1L, 5L, 3L);

        // Then
        assertThat(updated).isTrue();
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("unread:chat:1")),
                eq("5"),
                eq("3"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 전체_unread_CAS_중_값이_바뀌면_false를_반환한다() {
        // Given: Redis의 현재 값이 기대값과 달라 스크립트가 갱신을 거부한 상황
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("unread:chat:1")),
                eq("5"),
                eq("3")))
                .thenReturn(0L);

        // When & Then
        assertThat(chatUnreadService.compareAndSetTotal(1L, 5L, 3L)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 전체_unread_CAS의_null_기대값은_키_없음을_뜻하는_빈문자열로_전달한다() {
        // Given
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("unread:chat:7")),
                eq(""),
                eq("2")))
                .thenReturn(1L);

        // When
        boolean updated = chatUnreadService.compareAndSetTotal(7L, null, 2L);

        // Then
        assertThat(updated).isTrue();
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("unread:chat:7")),
                eq(""),
                eq("2"));
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
