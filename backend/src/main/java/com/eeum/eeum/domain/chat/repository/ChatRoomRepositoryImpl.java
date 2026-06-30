package com.eeum.eeum.domain.chat.repository;

import com.eeum.eeum.application.chat.dto.request.ChatRoomAdminSearchDto;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.entity.QChatParticipant;
import com.eeum.eeum.domain.chat.entity.QChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatRoomRepositoryImpl implements ChatRoomRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private final QChatRoom room = QChatRoom.chatRoom;
    private final QChatParticipant participant = QChatParticipant.chatParticipant;

    @Override
    public Slice<ChatRoom> findMyRooms(Long accountId, Pageable pageable) {
        int size = pageable.getPageSize();
        List<ChatRoom> content = queryFactory
                .select(room)
                .from(participant)
                .join(participant.chatRoom, room)
                .where(
                        participant.account.accountId.eq(accountId),
                        participant.status.eq(ParticipantStatus.ACTIVE),
                        room.isActive.isTrue()
                )
                .orderBy(room.lastMessageAt.desc().nullsLast(), room.chatroomId.desc())
                .offset(pageable.getOffset())
                .limit(size + 1L)
                .fetch();

        boolean hasNext = content.size() > size;
        if (hasNext) {
            content = content.subList(0, size);
        }
        return new SliceImpl<>(content, pageable, hasNext);
    }

    @Override
    public Slice<ChatRoom> findPublicRooms(Long regionId, Pageable pageable) {
        int size = pageable.getPageSize();
        List<ChatRoom> content = queryFactory
                .selectFrom(room)
                .where(
                        room.region.regionId.eq(regionId),
                        room.type.in(ChatRoomType.GROUP, ChatRoomType.GROUP_STREET),
                        room.isActive.isTrue()
                )
                .orderBy(room.lastMessageAt.desc().nullsLast(), room.chatroomId.desc())
                .offset(pageable.getOffset())
                .limit(size + 1L)
                .fetch();

        boolean hasNext = content.size() > size;
        if (hasNext) {
            content = content.subList(0, size);
        }
        return new SliceImpl<>(content, pageable, hasNext);
    }

    @Override
    public Page<ChatRoom> searchRoomsByAdmin(ChatRoomAdminSearchDto condition, Pageable pageable) {
        List<ChatRoom> content = queryFactory
                .selectFrom(room)
                .where(
                        typeEq(condition.getType()),
                        activeEq(condition.getIsActive()),
                        createdAfter(condition.getFrom()),
                        createdBefore(condition.getTo())
                )
                .orderBy(room.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(room.count())
                .from(room)
                .where(
                        typeEq(condition.getType()),
                        activeEq(condition.getIsActive()),
                        createdAfter(condition.getFrom()),
                        createdBefore(condition.getTo())
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    // ===================== 조건 빌더 =====================

    private BooleanExpression typeEq(ChatRoomType type) {
        return type != null ? room.type.eq(type) : null;
    }

    private BooleanExpression activeEq(Boolean isActive) {
        return isActive != null ? room.isActive.eq(isActive) : null;
    }

    private BooleanExpression createdAfter(LocalDateTime from) {
        return from != null ? room.createdAt.goe(from) : null;
    }

    private BooleanExpression createdBefore(LocalDateTime to) {
        return to != null ? room.createdAt.loe(to) : null;
    }
}
