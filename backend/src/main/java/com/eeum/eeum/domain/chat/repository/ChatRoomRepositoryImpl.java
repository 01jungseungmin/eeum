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
import com.eeum.eeum.common.dto.response.CursorSlice;
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
     * 이 값을 응답({@code CursorSlice.sort})에 그대로 실어 보내지 않으면
     * 클라이언트가 응답만 보고는 어떤 순서인지 알 수 없다.
     */
    private static final Sort ROOM_LIST_SORT = Sort.by(
            Sort.Order.desc("lastMessageAt").nullsLast(),
            Sort.Order.desc("chatroomId"));

    private final QChatRoom room = QChatRoom.chatRoom;
    private final QChatParticipant participant = QChatParticipant.chatParticipant;

    @Override
    public CursorSlice<ChatRoom> findMyRooms(
            Long accountId, ChatRoomCursor cursor, int size, boolean includeClosed) {
        List<ChatRoom> content = queryFactory
                .select(room)
                .from(participant)
                .join(participant.chatRoom, room)
                .where(
                        participant.account.accountId.eq(accountId),
                        participant.status.eq(ParticipantStatus.ACTIVE),
                        activeOnly(includeClosed),
                        afterCursor(cursor)
                )
                .orderBy(room.lastMessageAt.desc().nullsLast(), room.chatroomId.desc())
                .limit(size + 1L)
                .fetch();

        return toSlice(content, size);
    }

    @Override
    public CursorSlice<ChatRoom> findPublicRooms(Long regionId, ChatRoomCursor cursor, int size) {
        List<ChatRoom> content = queryFactory
                .selectFrom(room)
                .where(
                        room.region.regionId.eq(regionId),
                        room.type.in(ChatRoomType.GROUP, ChatRoomType.GROUP_STREET),
                        room.isActive.isTrue(),
                        afterCursor(cursor)
                )
                .orderBy(room.lastMessageAt.desc().nullsLast(), room.chatroomId.desc())
                .limit(size + 1L)
                .fetch();

        return toSlice(content, size);
    }

    /**
     * 커서(keyset) 페이징. OFFSET을 쓰지 않는다.
     *
     * <p>최근 대화순 목록은 메시지가 오는 순간 그 방이 맨 앞으로 올라온다. OFFSET은 그 밀림을
     * 그대로 맞아 경계에 있던 방이 다음 페이지에서 중복으로 나온다.
     *
     * <p>다음 커서는 서버가 만들어 응답에 싣는다. 페이지 번호가 없는 계약이라
     * {@link CursorSlice}로 돌려준다 — Slice로 돌리면 두 번째 페이지에도 number=0,
     * first=true가 실려 응답이 실제 위치를 잘못 설명한다.
     *
     * <p>마지막 방의 대화 시각이 null이면 커서 값도 null이다 — 대화 없는 방 구간은
     * 방 ID만으로 이어 읽는다.
     */
    private CursorSlice<ChatRoom> toSlice(List<ChatRoom> fetched, int size) {
        boolean hasNext = fetched.size() > size;
        List<ChatRoom> content = hasNext ? fetched.subList(0, size) : fetched;
        ChatRoom last = content.isEmpty() ? null : content.get(content.size() - 1);

        return CursorSlice.of(
                content,
                hasNext,
                last == null || last.getLastMessageAt() == null
                        ? null : last.getLastMessageAt().toString(),
                last == null ? null : last.getChatroomId(),
                ROOM_LIST_SORT);
    }

    /**
     * 커서 이후 구간. 정렬이 {@code lastMessageAt desc nulls last, chatroomId desc}이므로
     * "대화가 더 오래됐거나, 같으면 방 ID가 더 작거나, 아예 대화가 없는" 방들이다.
     *
     * <p>세 번째 항(대화 없는 방)을 빼면 그 방들이 목록에서 통째로 사라진다 —
     * NULL 비교는 참이 되지 않아 앞의 두 조건에 걸리지 않기 때문이다.
     *
     * <p>커서 자신이 대화 없는 방이면 이미 NULL 구간에 들어선 것이라 ID로만 뒤로 간다.
     */
    private BooleanExpression afterCursor(ChatRoomCursor cursor) {
        if (cursor == null) {
            return null;
        }
        if (cursor.lastMessageAt() == null) {
            return room.lastMessageAt.isNull().and(room.chatroomId.lt(cursor.chatroomId()));
        }
        return room.lastMessageAt.lt(cursor.lastMessageAt())
                .or(room.lastMessageAt.eq(cursor.lastMessageAt())
                        .and(room.chatroomId.lt(cursor.chatroomId())))
                .or(room.lastMessageAt.isNull());
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
