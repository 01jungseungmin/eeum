package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.dto.request.ChatRoomAdminSearchDto;
import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminChatServiceTest {

    @InjectMocks
    private AdminChatService adminChatService;

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatParticipantRepository chatParticipantRepository;
    @Mock private ChatRoomService chatRoomService;

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

    // ===================== getAllRooms =====================

    @Test
    void 관리자_전체_채팅방_조회_마지막메시지있음() {
        // Given
        Long roomId = 10L;
        Account creator = createAccount(1L, "홍길동");
        ChatRoom room = createGroupRoom(roomId, creator);
        ChatMessage lastMessage = ChatMessage.text(room, creator, "마지막 메시지");
        Page<ChatRoom> roomPage = new PageImpl<>(List.of(room));
        ChatRoomAdminSearchDto condition = ChatRoomAdminSearchDto.builder().build();
        Pageable pageable = PageRequest.of(0, 10);

        when(chatRoomRepository.searchRoomsByAdmin(condition, pageable)).thenReturn(roomPage);
        when(chatParticipantRepository.countGroupedByRoomIdsAndStatus(List.of(roomId), ParticipantStatus.ACTIVE))
                .thenReturn(List.<Object[]>of(new Object[]{roomId, 3L}));
        when(chatMessageRepository.findLatestMessagesForRooms(List.of(roomId)))
                .thenReturn(List.of(lastMessage));

        // When
        Page<ChatRoomResponseDto> result = adminChatService.getAllRooms(condition, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        ChatRoomResponseDto dto = result.getContent().get(0);
        assertThat(dto.getParticipantCount()).isEqualTo(3L);
        assertThat(dto.getLastMessagePreview()).isEqualTo("마지막 메시지");
        assertThat(dto.getUnreadCount()).isZero();
    }

    @Test
    void 관리자_전체_채팅방_조회_마지막메시지없음_preview_null() {
        // Given
        Long roomId = 10L;
        Account creator = createAccount(1L, "홍길동");
        ChatRoom room = createGroupRoom(roomId, creator);
        Page<ChatRoom> roomPage = new PageImpl<>(List.of(room));
        ChatRoomAdminSearchDto condition = ChatRoomAdminSearchDto.builder().build();
        Pageable pageable = PageRequest.of(0, 10);

        when(chatRoomRepository.searchRoomsByAdmin(condition, pageable)).thenReturn(roomPage);
        when(chatParticipantRepository.countGroupedByRoomIdsAndStatus(List.of(roomId), ParticipantStatus.ACTIVE))
                .thenReturn(List.of());
        when(chatMessageRepository.findLatestMessagesForRooms(List.of(roomId)))
                .thenReturn(List.of());

        // When
        Page<ChatRoomResponseDto> result = adminChatService.getAllRooms(condition, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getLastMessagePreview()).isNull();
    }

    @Test
    void 관리자_전체_채팅방_조회_결과없음() {
        // Given
        ChatRoomAdminSearchDto condition = ChatRoomAdminSearchDto.builder().build();
        Pageable pageable = PageRequest.of(0, 10);

        when(chatRoomRepository.searchRoomsByAdmin(condition, pageable)).thenReturn(Page.empty());

        // When
        Page<ChatRoomResponseDto> result = adminChatService.getAllRooms(condition, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        verify(chatParticipantRepository, never()).countGroupedByRoomIdsAndStatus(any(), any());
        verify(chatMessageRepository, never()).findLatestMessagesForRooms(any());
    }

    // ===================== getRoomMessages =====================

    @Test
    void 관리자_채팅방_메시지_조회_성공() {
        // Given
        Long roomId = 10L;
        Account creator = createAccount(1L, "홍길동");
        ChatRoom room = createGroupRoom(roomId, creator);
        ChatMessage message = ChatMessage.text(room, creator, "신고된 메시지");
        Page<ChatMessage> messagePage = new PageImpl<>(List.of(message));
        Pageable pageable = PageRequest.of(0, 20);

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(chatMessageRepository.findAllByChatRoom_ChatroomIdOrderBySentAtDesc(roomId, pageable))
                .thenReturn(messagePage);

        // When
        Page<ChatMessageResponseDto> result = adminChatService.getRoomMessages(roomId, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void 관리자_채팅방_메시지_조회_방없음_예외() {
        // Given
        Long roomId = 99L;
        Pageable pageable = PageRequest.of(0, 20);

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> adminChatService.getRoomMessages(roomId, pageable))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);

        verify(chatMessageRepository, never()).findAllByChatRoom_ChatroomIdOrderBySentAtDesc(any(), any());
    }

    // ===================== forceDeleteMessage =====================

    @Test
    void 관리자_메시지_강제삭제_성공() {
        // Given
        Long messageId = 100L;
        Account creator = createAccount(1L, "홍길동");
        ChatRoom room = createGroupRoom(10L, creator);
        ChatMessage message = ChatMessage.text(room, creator, "삭제 대상 메시지");

        when(chatMessageRepository.findById(messageId)).thenReturn(Optional.of(message));

        // When
        adminChatService.forceDeleteMessage(messageId);

        // Then
        assertThat(message.isDeleted()).isTrue();
    }

    @Test
    void 관리자_메시지_강제삭제_메시지없음_예외() {
        // Given
        Long messageId = 999L;

        when(chatMessageRepository.findById(messageId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> adminChatService.forceDeleteMessage(messageId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_MESSAGE_NOT_FOUND);
    }

    @Test
    void 관리자_메시지_강제삭제_이미삭제된메시지도_markDeleted_재호출() {
        // Given
        Long messageId = 100L;
        Account creator = createAccount(1L, "홍길동");
        ChatRoom room = createGroupRoom(10L, creator);
        ChatMessage message = ChatMessage.text(room, creator, "이미 삭제된 메시지");
        message.markDeleted();

        when(chatMessageRepository.findById(messageId)).thenReturn(Optional.of(message));

        // When
        adminChatService.forceDeleteMessage(messageId);

        // Then — 관리자는 이미 삭제된 메시지도 예외 없이 처리
        assertThat(message.isDeleted()).isTrue();
    }

    // ===================== forceDeactivateRoom =====================

    @Test
    void 관리자_채팅방_강제비활성화는_ChatRoomService에_위임된다() {
        // Given: 여기서 직접 deactivate()하면 생성과 동일한 분산 락을 잡지 못해
        //        재생성 요청과 레이스가 나고, unread 회수/종료 통지도 누락된다
        Long roomId = 10L;

        // When
        adminChatService.forceDeactivateRoom(roomId);

        // Then: 락·정리 절차를 소유한 쪽으로 위임하고, 자체적으로 방을 조회·수정하지 않는다
        verify(chatRoomService).forceCloseRoom(roomId);
        verify(chatRoomRepository, never()).findById(roomId);
    }

    @Test
    void 관리자_채팅방_강제비활성화_방없음_예외가_전파된다() {
        // Given
        Long roomId = 99L;
        doThrow(new NotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND))
                .when(chatRoomService).forceCloseRoom(roomId);

        // When & Then
        assertThatThrownBy(() -> adminChatService.forceDeactivateRoom(roomId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }
}
