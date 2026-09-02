package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.entity.QChatMessage;
import com.eeum.eeum.common.dto.response.CursorSlice;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private final QChatMessage message = QChatMessage.chatMessage;

    /**
     * 메시지 목록 정렬 — 최신순, 동률은 PK로 끊는다.
     *
     * <p>이 값 하나가 실제 SQL(orderBy)과 응답 메타데이터({@code CursorSlice.sort}) 양쪽의 근거다.
     */
    private static final Sort MESSAGE_SORT = Sort.by(
            Sort.Order.desc("sentAt"), Sort.Order.desc("chatMessageId"));

    /**
     * 커서(keyset) 페이징. 페이지 번호가 없는 계약이라 {@link CursorSlice}로 돌려준다 —
     * Slice로 돌리면 두 번째 페이지에도 number=0, first=true가 실려 위치를 잘못 설명한다.
     *
     * <p>다음 커서는 서버가 만들어 응답에 싣는다. 클라이언트는 그대로 되돌려보내면 된다.
     */
    @Override
    public CursorSlice<ChatMessage> findRoomMessages(
            Long roomId, ChatMessageCursor cursor, int size) {
        List<ChatMessage> fetched = queryFactory
                .selectFrom(message)
                .where(message.chatRoom.chatroomId.eq(roomId), afterCursor(cursor))
                .orderBy(message.sentAt.desc(), message.chatmessageId.desc())
                .limit(size + 1L)
                .fetch();

        boolean hasNext = fetched.size() > size;
        List<ChatMessage> content = hasNext ? fetched.subList(0, size) : fetched;
        ChatMessage last = content.isEmpty() ? null : content.get(content.size() - 1);

        return CursorSlice.of(
                content,
                hasNext,
                last == null ? null : last.getSentAt().toString(),
                last == null ? null : last.getChatmessageId(),
                MESSAGE_SORT);
    }

    /**
     * 커서 이후(= 더 과거) 구간. "발신 시각이 더 이르거나, 같으면 ID가 더 작은" 메시지다.
     *
     * <p>두 번째 항이 빠지면 같은 시각에 저장된 메시지가 페이지 경계에서 통째로 사라진다 —
     * {@code sentAt < cursor} 하나로는 그 시각의 나머지 메시지를 다시 볼 방법이 없다.
     */
    private BooleanExpression afterCursor(ChatMessageCursor cursor) {
        if (cursor == null) {
            return null;
        }
        return message.sentAt.lt(cursor.sentAt())
                .or(message.sentAt.eq(cursor.sentAt())
                        .and(message.chatmessageId.lt(cursor.chatMessageId())));
    }

    @Override
    public List<ChatMessage> findLatestMessagesForRooms(List<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }

        // Step 1: 방별 최신 chatmessageId 조회 (GROUP BY + MAX — sentAt 동일 밀리초 충돌 방지)
        List<Long> latestIds = queryFactory
                .select(message.chatmessageId.max())
                .from(message)
                .where(message.chatRoom.chatroomId.in(roomIds))
                .groupBy(message.chatRoom.chatroomId)
                .fetch()
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (latestIds.isEmpty()) {
            return List.of();
        }

        // Step 2: 해당 ID의 메시지 엔티티 조회
        return queryFactory
                .selectFrom(message)
                .where(message.chatmessageId.in(latestIds))
                .fetch();
    }
}
