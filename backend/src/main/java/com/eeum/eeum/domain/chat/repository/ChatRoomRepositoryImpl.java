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
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatRoomRepositoryImpl implements ChatRoomRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 채팅방 목록 정렬 — 최근 대화순, 대화 없는 방은 뒤로, 동률은 PK로 끊는다.
     *
     * <p>실제 SQL과 응답 메타데이터가 같은 값에서 나오도록 여기 한 곳에서만 정한다.
     * 요청 Pageable을 그대로 SliceImpl에 넘기면 sort가 UNSORTED로 나가,
     * 클라이언트가 응답만 보고는 어떤 순서인지 알 수 없다.
     */
    private static final Sort ROOM_LIST_SORT = Sort.by(
            Sort.Order.desc("lastMessageAt").nullsLast(),
            Sort.Order.desc("chatroomId"));

    private final QChatRoom room = QChatRoom.chatRoom;
    private final QChatParticipant participant = QChatParticipant.chatParticipant;

    @Override
    public Slice<ChatRoom> findMyRooms(Long accountId, Pageable pageable, boolean includeClosed) {
        int size = pageable.getPageSize();
        List<ChatRoom> content = queryFactory
                .select(room)
                .from(participant)
                .join(participant.chatRoom, room)
                .where(
                        participant.account.accountId.eq(accountId),
                        participant.status.eq(ParticipantStatus.ACTIVE),
                        activeOnly(includeClosed)
                )
                .orderBy(room.lastMessageAt.desc().nullsLast(), room.chatroomId.desc())
                .offset(pageable.getOffset())
                .limit(size + 1L)
                .fetch();

        boolean hasNext = content.size() > size;
        if (hasNext) {
            content = content.subList(0, size);
        }
        return new SliceImpl<>(content, appliedPageable(pageable), hasNext);
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
        return new SliceImpl<>(content, appliedPageable(pageable), hasNext);
    }

    private Pageable appliedPageable(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ROOM_LIST_SORT);
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

    // includeClosed=true면 활성 조건을 걸지 않는다 (null 반환 시 QueryDSL이 조건에서 제외)
    private BooleanExpression activeOnly(boolean includeClosed) {
        return includeClosed ? null : room.isActive.isTrue();
    }

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
