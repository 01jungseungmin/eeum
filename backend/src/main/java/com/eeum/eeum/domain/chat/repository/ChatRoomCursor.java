package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 채팅방 목록 커서 — 직전 페이지의 마지막 방 위치.
 *
 * <p>OFFSET을 쓰지 않는 이유가 이 타입의 존재 이유다. 목록은 최근 대화순이라 메시지가 오면
 * 그 방이 맨 앞으로 올라온다. 1페이지를 읽고 2페이지를 요청하는 사이 대화가 한 번만 오가도
 * 목록 전체가 밀려, 경계에 있던 방이 2페이지에서 다시 나온다. 채팅은 후기보다 훨씬 자주
 * 움직이므로 이 중복이 눈에 띄게 잦다.
 *
 * <p>정렬 키가 {@code lastMessageAt desc nulls last, chatroomId desc} 두 개라 커서도 둘을 함께
 * 가진다. {@code lastMessageAt}은 <b>null일 수 있다</b> — 대화가 한 번도 없는 방이다.
 * 그 구간에 들어선 커서는 시각이 없고 방 ID만 있으므로, 이 레코드는 시각 null을 정상으로 받는다.
 *
 * <p><b>한계는 남는다.</b> 정렬 키가 계속 변하는 목록이라, 커서보다 뒤에 있던 방에 메시지가 와서
 * 맨 앞으로 올라가면 남은 페이지에서는 보이지 않는다. 이는 OFFSET의 중복과 달리
 * "이미 지나간 위치"의 문제라 커서로 없앨 수 없다 — 클라이언트가 목록 상단을 갱신하는 경로
 * (WebSocket 수신, 당겨서 새로고침)로 메운다.
 */
public record ChatRoomCursor(LocalDateTime lastMessageAt, Long chatroomId) {

    /**
     * 요청 파라미터를 커서로 바꾼다. 방 ID가 없으면 첫 페이지라 {@code null}을 돌려준다.
     *
     * <p>시각만 보내는 것은 막는다. 방 ID가 없으면 같은 시각 방들 사이에서 경계를 끊지 못해
     * OFFSET과 똑같은 중복·누락이 생기는데, 조용히 첫 페이지를 돌려주면
     * 무한 스크롤이 같은 목록을 반복하게 된다.
     */
    public static ChatRoomCursor ofNullable(LocalDateTime lastMessageAt, Long chatroomId) {
        if (chatroomId == null) {
            if (lastMessageAt != null) {
                throw new BadRequestException(ErrorCode.CHAT_INVALID_CURSOR);
            }
            return null;
        }
        // 시각이 없는 커서는 정상이다 — 대화가 없는 방 구간을 가리킨다.
        return new ChatRoomCursor(lastMessageAt, chatroomId);
    }
}
