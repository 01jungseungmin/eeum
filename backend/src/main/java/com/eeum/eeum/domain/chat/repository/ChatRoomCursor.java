package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** 채팅방 목록 커서. 최근 대화순 정렬 키를 함께 보관한다. */
public record ChatRoomCursor(LocalDateTime lastMessageAt, Long chatroomId) {

    /**
     * 요청 파라미터를 커서로 바꾼다. 방 ID가 없으면 첫 페이지라 null을 돌려준다.
     *
     * 값만 보내는 것은 막는다. 방 ID가 없으면 같은 시각 방들 사이에서 경계를 끊지 못해
     * OFFSET과 똑같은 중복·누락이 생기는데, 조용히 첫 페이지를 돌려주면
     * 무한 스크롤이 같은 목록을 반복하게 된다.
     *
     * 값은 문자열로 받는다 — 응답의 nextCursorValue를 그대로 되돌려보내는 계약이라
     * 목록마다 다른 타입을 클라이언트가 알 필요가 없다.
     */
    public static ChatRoomCursor ofNullable(String cursorValue, Long chatroomId) {
        // 첫 페이지는 "둘 다 보내지 않은" 경우뿐이다. 값만 보냈다면(빈 값이라도) 커서를 보냈다고
        // 믿는 클라이언트에게 첫 페이지를 돌려주는 셈이라 목록이 반복된다.
        if (cursorValue == null && chatroomId == null) {
            return null;
        }
        if (chatroomId == null) {
            throw new BadRequestException(ErrorCode.CHAT_INVALID_CURSOR);
        }
        // 방 ID와 함께 온 빈 값은 정상이다 — 대화가 없는 방 구간(lastMessageAt이 null)을 가리킨다.
        boolean blankValue = cursorValue == null || cursorValue.isBlank();
        return new ChatRoomCursor(
                blankValue ? null : parseLastMessageAt(cursorValue.trim()), chatroomId);
    }

    /**
     * 커서 값은 응답의 nextCursorValue를 그대로 되돌려보낸 것이다. 형식이 어긋났다는 것은
     * 클라이언트가 값을 직접 만들었거나 다른 목록의 커서를 보냈다는 뜻이라 조용히 넘기지 않는다.
     */
    private static LocalDateTime parseLastMessageAt(String rawValue) {
        try {
            return LocalDateTime.parse(rawValue);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(ErrorCode.CHAT_INVALID_CURSOR);
        }
    }
}
