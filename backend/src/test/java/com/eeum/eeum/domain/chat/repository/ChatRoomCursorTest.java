package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 채팅방 목록 커서 파라미터 해석.
 *
 * <p>정렬이 {@code lastMessageAt desc nulls last, chatroomId desc}이므로 커서는 방 ID가 있어야
 * 성립한다. 시각은 없을 수 있다 — 대화가 한 번도 없는 방 구간을 가리키는 정상적인 커서다.
 */
class ChatRoomCursorTest {

    private static final LocalDateTime LAST_MESSAGE_AT = LocalDateTime.of(2026, 9, 1, 10, 0);
    private static final String LAST_MESSAGE_VALUE = LAST_MESSAGE_AT.toString();

    @Test
    void 둘_다_없으면_첫_페이지다() {
        assertThat(ChatRoomCursor.ofNullable(null, null)).isNull();
    }

    @Test
    void 둘_다_있으면_커서를_만든다() {
        ChatRoomCursor cursor = ChatRoomCursor.ofNullable(LAST_MESSAGE_VALUE, 7L);

        assertThat(cursor).isNotNull();
        assertThat(cursor.lastMessageAt()).isEqualTo(LAST_MESSAGE_AT);
        assertThat(cursor.chatroomId()).isEqualTo(7L);
    }

    @Test
    void 대화가_없는_방_구간은_방_ID만으로_이어_읽는다() {
        // lastMessageAt이 null인 방들이 목록 맨 뒤에 모여 있다. 그 구간의 커서에는 담을 시각이 없다.
        ChatRoomCursor cursor = ChatRoomCursor.ofNullable(null, 7L);

        assertThat(cursor).isNotNull();
        assertThat(cursor.lastMessageAt()).isNull();
        assertThat(cursor.chatroomId()).isEqualTo(7L);
    }

    @Test
    void 빈_값만_보내도_첫_페이지가_아니다() {
        // 대화 없는 방 구간의 커서는 값이 비지만 방 ID가 함께 온다.
        // 방 ID 없이 빈 값만 오면 커서를 보냈다고 믿는 클라이언트가 첫 페이지를 반복해서 받는다.
        assertThatThrownBy(() -> ChatRoomCursor.ofNullable("", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_INVALID_CURSOR);
    }

    @Test
    void 값의_형식이_어긋나면_거절한다() {
        assertThatThrownBy(() -> ChatRoomCursor.ofNullable("어제", 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_INVALID_CURSOR);
    }

    @Test
    void 값만_보내면_거절한다() {
        // 방 ID가 없으면 같은 시각 방들 사이에서 경계를 끊지 못해 OFFSET과 같은 중복이 난다.
        assertThatThrownBy(() -> ChatRoomCursor.ofNullable(LAST_MESSAGE_VALUE, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_INVALID_CURSOR);
    }
}
