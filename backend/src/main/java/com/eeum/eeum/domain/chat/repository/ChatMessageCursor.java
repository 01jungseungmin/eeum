package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** 메시지 목록 커서. sentAt과 PK를 함께 사용해 같은 시각의 누락을 막는다. */
public record ChatMessageCursor(LocalDateTime sentAt, Long chatMessageId) {

    /**
     * 요청 파라미터를 커서로 바꾼다. 둘 다 보내지 않으면 첫 페이지라 null을 돌려준다.
     *
     * 한쪽만 보내는 것은 막는다 — 빈 문자열도 "보낸 것"으로 본다. 조용히 첫 페이지를
     * 돌려주면 클라이언트는 과거 메시지를 받았다고 믿는데 화면에는 같은 목록이 다시 쌓인다.
     */
    public static ChatMessageCursor ofNullable(String cursorValue, Long chatMessageId) {
        if (cursorValue == null && chatMessageId == null) {
            return null;
        }
        if (chatMessageId == null || cursorValue == null || cursorValue.isBlank()) {
            throw new BadRequestException(ErrorCode.CHAT_MESSAGE_INVALID_CURSOR);
        }
        return new ChatMessageCursor(parseSentAt(cursorValue.trim()), chatMessageId);
    }

    /**
     * 커서 값은 응답의 nextCursorValue를 그대로 되돌려보낸 것이다. 형식이 어긋났다는 것은
     * 클라이언트가 값을 직접 만들었거나 다른 목록의 커서를 보냈다는 뜻이라 조용히 넘기지 않는다.
     */
    private static LocalDateTime parseSentAt(String rawValue) {
        try {
            return LocalDateTime.parse(rawValue);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(ErrorCode.CHAT_MESSAGE_INVALID_CURSOR);
        }
    }
}
