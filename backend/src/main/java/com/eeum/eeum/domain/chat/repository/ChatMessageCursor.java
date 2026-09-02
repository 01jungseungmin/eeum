package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 메시지 목록 커서 — 직전 페이지의 마지막 메시지 위치.
 *
 * <p><b>시각만으로는 부족하다.</b> 예전 커서는 {@code sentAt} 하나였고 조건이
 * {@code sentAt < cursor}였다. 같은 시각에 저장된 메시지가 페이지 경계에 걸리면 그중 하나만
 * 내려간 뒤 나머지가 조건에서 제외돼 <b>영구히 누락</b>된다 — 다시 스크롤해도 나오지 않는다.
 * 정렬에도 tie-break가 없어 같은 시각 메시지들의 순서가 조회마다 달라질 수 있었다.
 *
 * <p>정렬 키를 {@code sentAt desc, chatMessageId desc}로 확정하고 커서도 둘을 함께 담는다.
 * 같은 시각이면 ID로 끊으므로 경계가 한 지점으로 고정된다.
 *
 * <p>메시지는 초당 여러 건이 같은 방에 쌓일 수 있어 시각 충돌이 다른 목록보다 흔하다.
 */
public record ChatMessageCursor(LocalDateTime sentAt, Long chatMessageId) {

    /**
     * 요청 파라미터를 커서로 바꾼다. 둘 다 보내지 않으면 첫 페이지라 {@code null}을 돌려준다.
     *
     * <p>한쪽만 보내는 것은 막는다 — 빈 문자열도 "보낸 것"으로 본다. 조용히 첫 페이지를
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
     * 커서 값은 응답의 {@code nextCursorValue}를 그대로 되돌려보낸 것이다. 형식이 어긋났다는 것은
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
