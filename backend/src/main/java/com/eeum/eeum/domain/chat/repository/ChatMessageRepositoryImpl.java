package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.entity.QChatMessage;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private final QChatMessage message = QChatMessage.chatMessage;

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
