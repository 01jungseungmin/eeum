package com.eeum.eeum.application.chat.helper;

import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAccessHelperTest {

    @InjectMocks
    private ChatAccessHelper chatAccessHelper;

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatParticipantRepository chatParticipantRepository;
    @Mock private ChatMessageRepository chatMessageRepository;

    // ===================== 픽스처 헬퍼 =====================

    private Account createAccount(Long id, String name) {
        Account account = Account.createUser(
                "user" + id + "@test.com", "encoded-pw", name, "nick" + id, "010-0000-0000");
        ReflectionTestUtils.setField(account, "accountId", id);
        return account;
    }

    private ChatRoom createGroupRoom(Long id, Account creator) {
        ChatRoom room = ChatRoom.createGroup(creator, ChatRoomType.GROUP, "테스트방", ChatRoomRefType.NONE, null, null);
        ReflectionTestUtils.setField(room, "chatroomId", id);
        return room;
    }

    private ChatRoom createPrivateRoom(Long id, Account creator) {
        ChatRoom room = ChatRoom.createGroup(creator, ChatRoomType.PRIVATE, "1:1 채팅", ChatRoomRefType.NONE, null, null);
        ReflectionTestUtils.setField(room, "chatroomId", id);
        return room;
    }

    // ===================== getRoomOrThrow =====================

    @Test
    void 채팅방_조회_성공() {
        // Given
        Long roomId = 10L;
        Account creator = createAccount(1L, "홍길동");
        ChatRoom room = createGroupRoom(roomId, creator);

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

        // When
        ChatRoom result = chatAccessHelper.getRoomOrThrow(roomId);

        // Then
        assertThat(result.getChatroomId()).isEqualTo(roomId);
    }

    @Test
    void 채팅방_조회_방없음_예외() {
        // Given
        Long roomId = 99L;

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> chatAccessHelper.getRoomOrThrow(roomId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    // ===================== verifyParticipant =====================

    @Test
    void 참여자_검증_ACTIVE_상태_성공() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, account);
        ChatParticipant participant = ChatParticipant.create(room, account);

        when(chatParticipantRepository.findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, accountId))
                .thenReturn(Optional.of(participant));

        // When
        ChatParticipant result = chatAccessHelper.verifyParticipant(accountId, roomId);

        // Then
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void 참여자_검증_참여_기록_없음_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;

        when(chatParticipantRepository.findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, accountId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> chatAccessHelper.verifyParticipant(accountId, roomId))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_NOT_PARTICIPANT);
    }

    @Test
    void 참여자_검증_LEFT_상태_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, account);
        ChatParticipant leftParticipant = ChatParticipant.create(room, account);
        leftParticipant.leave();

        when(chatParticipantRepository.findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, accountId))
                .thenReturn(Optional.of(leftParticipant));

        // When & Then
        assertThatThrownBy(() -> chatAccessHelper.verifyParticipant(accountId, roomId))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_NOT_PARTICIPANT);
    }

    // ===================== verifyMessageOwnership =====================

    @Test
    void 메시지_소유권_검증_성공() {
        // Given
        Long accountId = 1L;
        Long messageId = 100L;
        Account account = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(10L, account);
        ChatMessage message = ChatMessage.text(room, account, "내 메시지");

        when(chatMessageRepository.findByChatmessageIdAndAccount_AccountId(messageId, accountId))
                .thenReturn(Optional.of(message));

        // When
        ChatMessage result = chatAccessHelper.verifyMessageOwnership(accountId, messageId);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void 메시지_소유권_검증_본인메시지아님_예외() {
        // Given
        Long accountId = 1L;
        Long messageId = 100L;

        when(chatMessageRepository.findByChatmessageIdAndAccount_AccountId(messageId, accountId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> chatAccessHelper.verifyMessageOwnership(accountId, messageId))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_MESSAGE_ACCESS_DENIED);
    }

    // ===================== verifyGroupRoom =====================

    @Test
    void 그룹방_검증_GROUP_타입_성공() {
        // Given
        Account creator = createAccount(1L, "홍길동");
        ChatRoom room = createGroupRoom(10L, creator);   // ChatRoomType.GROUP

        // When & Then — 예외 없이 통과
        assertThatCode(() -> chatAccessHelper.verifyGroupRoom(room))
                .doesNotThrowAnyException();
    }

    @Test
    void 그룹방_검증_GROUP_STREET_타입_성공() {
        // Given
        Account creator = createAccount(1L, "홍길동");
        ChatRoom room = ChatRoom.createGroup(creator, ChatRoomType.GROUP_STREET, "동네방", ChatRoomRefType.NONE, null, null);
        ReflectionTestUtils.setField(room, "chatroomId", 10L);

        // When & Then
        assertThatCode(() -> chatAccessHelper.verifyGroupRoom(room))
                .doesNotThrowAnyException();
    }

    @Test
    void 그룹방_검증_PRIVATE_타입_예외() {
        // Given
        Account creator = createAccount(1L, "홍길동");
        ChatRoom room = createPrivateRoom(10L, creator);

        // When & Then
        assertThatThrownBy(() -> chatAccessHelper.verifyGroupRoom(room))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_NOT_GROUP_ROOM);
    }

    // ===================== verifyRoomActive =====================

    @Test
    void 활성방_검증_활성상태_성공() {
        // Given
        Account creator = createAccount(1L, "홍길동");
        ChatRoom room = createGroupRoom(10L, creator);   // isActive = true 기본값

        // When & Then
        assertThatCode(() -> chatAccessHelper.verifyRoomActive(room))
                .doesNotThrowAnyException();
    }

    @Test
    void 활성방_검증_비활성상태_예외() {
        // Given
        Account creator = createAccount(1L, "홍길동");
        ChatRoom room = createGroupRoom(10L, creator);
        room.deactivate();

        // When & Then
        assertThatThrownBy(() -> chatAccessHelper.verifyRoomActive(room))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_INACTIVE);
    }
}
