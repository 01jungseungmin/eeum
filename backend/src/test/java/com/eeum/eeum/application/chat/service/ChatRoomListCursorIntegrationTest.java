package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.dto.response.ChatRoomResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.eeum.eeum.common.dto.response.CursorSlice;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import com.eeum.eeum.support.IntegrationTestSupport;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 내 채팅방 목록의 커서 페이징.
 *
 * <p>이 목록은 최근 대화순이라 메시지가 오는 순간 그 방이 맨 앞으로 올라온다.
 * OFFSET으로 넘기면 페이지를 읽는 사이 대화가 한 번만 오가도 목록 전체가 밀려,
 * 경계에 있던 방이 다음 페이지에서 그대로 다시 나왔다. 그 결함을 고정한다.
 *
 * <p>방은 서비스가 아니라 리포지토리로 직접 만든다 — 서비스 생성 경로는 같은 참여자 조합의
 * 중복 방을 막기 때문에 목록 픽스처(같은 사람이 속한 방 여러 개)를 만들 수 없다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class ChatRoomListCursorIntegrationTest extends IntegrationTestSupport {

    private final ChatRoomService chatRoomService;
    private final AccountRepository accountRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 1, 10, 0);

    private Long memberId;
    private Account member;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        member = accountRepository.save(Account.createUser(
                "cursor-" + tag + "@test.com", "pw", "커서", "커서" + tag, "010-9999-9999"));
        memberId = member.getAccountId();
    }

    @AfterEach
    void tearDown() {
        chatParticipantRepository.deleteAll();
        chatRoomRepository.deleteAll();
    }

    @Test
    void 커서로_이어_읽으면_방이_겹치지_않는다() {
        // Given: 최근 대화순으로 방3 > 방2 > 방1
        Long room1 = room("방1", BASE.minusHours(3));
        Long room2 = room("방2", BASE.minusHours(2));
        Long room3 = room("방3", BASE.minusHours(1));

        // When
        CursorSlice<ChatRoomResponseDto> first = chatRoomService.getMyRooms(memberId, null, null, 2, false);
        CursorSlice<ChatRoomResponseDto> second =
                chatRoomService.getMyRooms(
                        memberId, cursorValue(first), cursorRoomId(first), 2, false);

        // Then
        assertThat(first.getContent()).extracting(ChatRoomResponseDto::getRoomId)
                .containsExactly(room3, room2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.getContent()).extracting(ChatRoomResponseDto::getRoomId)
                .containsExactly(room1);
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    void 페이지를_읽는_사이_대화가_와도_경계가_중복되지_않는다() {
        // Given: OFFSET이었다면 아래에서 방2가 두 번 나온다.
        Long room1 = room("방1", BASE.minusHours(3));
        Long room2 = room("방2", BASE.minusHours(2));
        Long room3 = room("방3", BASE.minusHours(1));

        CursorSlice<ChatRoomResponseDto> first = chatRoomService.getMyRooms(memberId, null, null, 2, false);
        assertThat(first.getContent()).extracting(ChatRoomResponseDto::getRoomId)
                .containsExactly(room3, room2);

        // When: 두 페이지를 읽는 사이 방1에 메시지가 와 맨 앞으로 올라간다
        bumpLastMessage(room1, BASE.plusMinutes(1));

        CursorSlice<ChatRoomResponseDto> second =
                chatRoomService.getMyRooms(
                        memberId, cursorValue(first), cursorRoomId(first), 2, false);

        // Then: 이미 본 방은 다시 오지 않는다. 맨 앞으로 올라간 방1은 커서보다 앞이라 빠진다 —
        // 커서가 없앨 수 있는 것은 "이미 본 것의 중복"이고, 위로 올라간 방은
        // 클라이언트가 목록 상단을 갱신하는 경로(WebSocket 수신)로 본다.
        assertThat(second.getContent()).extracting(ChatRoomResponseDto::getRoomId)
                .doesNotContain(room3, room2);
    }

    @Test
    void 대화가_없는_방은_뒤에_오고_커서로_이어_읽힌다() {
        // Given: lastMessageAt이 null인 방은 정렬상 맨 뒤다. 커서에 담을 시각이 없어
        // 방 ID만으로 이어 읽어야 하는데, 그 분기를 빠뜨리면 이 방들이 목록에서 통째로 사라진다.
        Long talked = room("대화한 방", BASE);
        Long silent1 = room("조용한 방1", null);
        Long silent2 = room("조용한 방2", null);

        // When
        CursorSlice<ChatRoomResponseDto> first = chatRoomService.getMyRooms(memberId, null, null, 2, false);
        CursorSlice<ChatRoomResponseDto> second =
                chatRoomService.getMyRooms(
                        memberId, cursorValue(first), cursorRoomId(first), 2, false);

        // Then: 대화한 방이 먼저, 조용한 방들이 뒤따라 나온다
        assertThat(first.getContent().get(0).getRoomId()).isEqualTo(talked);
        assertThat(first.getContent()).hasSize(2);
        assertThat(second.getContent()).extracting(ChatRoomResponseDto::getRoomId)
                .doesNotContain(talked);
        // 세 방이 중복 없이 모두 나온다
        assertThat(first.getContent().size() + second.getContent().size()).isEqualTo(3);
        assertThat(second.getContent()).extracting(ChatRoomResponseDto::getRoomId)
                .isSubsetOf(silent1, silent2);
    }

    // 직전 페이지의 마지막 방 = 다음 요청의 커서. 응답 필드(lastMessageAt·roomId)만으로 만든다.
    // 서버가 준 다음 커서를 그대로 되돌려보낸다 — 클라이언트가 할 일이 정확히 이것이다.
    private String cursorValue(CursorSlice<ChatRoomResponseDto> page) {
        return page.getNextCursorValue();
    }

    private Long cursorRoomId(CursorSlice<ChatRoomResponseDto> page) {
        return page.getNextCursorId();
    }

    private Long room(String name, LocalDateTime lastMessageAt) {
        ChatRoom room = ChatRoom.createGroup(
                member, ChatRoomType.GROUP, name, ChatRoomRefType.NONE, null, null);
        if (lastMessageAt != null) {
            room.updateLastMessageAt(lastMessageAt);
        }
        ChatRoom saved = chatRoomRepository.save(room);
        chatParticipantRepository.save(ChatParticipant.create(saved, member));
        return saved.getChatroomId();
    }

    private void bumpLastMessage(Long roomId, LocalDateTime sentAt) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();
        room.updateLastMessageAt(sentAt);
        chatRoomRepository.saveAndFlush(room);
    }
}
