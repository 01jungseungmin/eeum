package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.dto.request.GroupChatRoomCreateRequestDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomResponseDto;
import com.eeum.eeum.application.chat.helper.ChatAccessHelper;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.eeum.eeum.domain.chat.event.ChatRoomReadEvent;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @InjectMocks
    private ChatRoomService chatRoomService;

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatParticipantRepository chatParticipantRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ChatAccessHelper chatAccessHelper;
    @Mock private ChatUnreadService chatUnreadService;
    @Mock private RedisLockService redisLockService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TransactionTemplate transactionTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpCommonStubs() {
        // Supplier 오버로드 — 람다를 직접 실행
        lenient().doAnswer(inv -> {
            Supplier<?> supplier = inv.getArgument(2);
            return supplier.get();
        }).when(redisLockService).executeWithLock(any(String.class), any(Duration.class), any(Supplier.class));

        // Runnable 오버로드 — 람다를 직접 실행
        lenient().doAnswer(inv -> {
            Runnable runnable = inv.getArgument(2);
            runnable.run();
            return null;
        }).when(redisLockService).executeWithLock(any(String.class), any(Duration.class), any(Runnable.class));

        // TransactionTemplate — 콜백을 직접 실행
        lenient().doAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(any());
    }

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

    private ChatParticipant createActiveParticipant(ChatRoom room, Account account) {
        return ChatParticipant.create(room, account);
    }

    private GroupChatRoomCreateRequestDto createGroupRequest(
            String name, ChatRoomType type, List<Long> participantIds) {
        GroupChatRoomCreateRequestDto dto = new GroupChatRoomCreateRequestDto();
        ReflectionTestUtils.setField(dto, "name", name);
        ReflectionTestUtils.setField(dto, "type", type);
        ReflectionTestUtils.setField(dto, "refType", ChatRoomRefType.NONE);
        ReflectionTestUtils.setField(dto, "refId", null);
        ReflectionTestUtils.setField(dto, "participantAccountIds", participantIds);
        return dto;
    }

    // ===================== createGroupRoom =====================

    @Test
    void 그룹채팅방_생성_성공() {
        // Given
        Long creatorId = 1L;
        Account creator = createAccount(creatorId, "홍길동");
        Account invitee = createAccount(2L, "이순신");
        GroupChatRoomCreateRequestDto request =
                createGroupRequest("테스트방", ChatRoomType.GROUP, List.of(2L));

        when(accountRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatParticipantRepository.save(any(ChatParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.findAllById(anyCollection())).thenReturn(List.of(invitee));
        when(chatParticipantRepository.countByChatRoom_ChatroomIdAndStatus(any(), eq(ParticipantStatus.ACTIVE)))
                .thenReturn(2L);
        when(chatUnreadService.getRoomUnread(any(), any(), any())).thenReturn(0L);
        when(chatMessageRepository.findFirstByChatRoom_ChatroomIdOrderBySentAtDesc(any()))
                .thenReturn(Optional.empty());

        // When
        ChatRoomResponseDto result = chatRoomService.createGroupRoom(creatorId, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("테스트방");
        verify(chatRoomRepository).save(any(ChatRoom.class));
        verify(chatParticipantRepository, times(2)).save(any(ChatParticipant.class)); // creator + invitee
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void 그룹채팅방_생성_초대자없음_성공() {
        // Given
        Long creatorId = 1L;
        Account creator = createAccount(creatorId, "홍길동");
        GroupChatRoomCreateRequestDto request = createGroupRequest("테스트방", null, null);

        when(accountRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatParticipantRepository.save(any(ChatParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatParticipantRepository.countByChatRoom_ChatroomIdAndStatus(any(), eq(ParticipantStatus.ACTIVE)))
                .thenReturn(1L);
        when(chatUnreadService.getRoomUnread(any(), any(), any())).thenReturn(0L);
        when(chatMessageRepository.findFirstByChatRoom_ChatroomIdOrderBySentAtDesc(any()))
                .thenReturn(Optional.empty());

        // When
        ChatRoomResponseDto result = chatRoomService.createGroupRoom(creatorId, request);

        // Then
        assertThat(result).isNotNull();
        verify(chatParticipantRepository, times(1)).save(any(ChatParticipant.class)); // creator only
    }

    @Test
    void 그룹채팅방_생성_PRIVATE타입_요청시_예외() {
        // Given
        Long creatorId = 1L;
        GroupChatRoomCreateRequestDto request =
                createGroupRequest("테스트방", ChatRoomType.PRIVATE, null);

        // When & Then
        assertThatThrownBy(() -> chatRoomService.createGroupRoom(creatorId, request))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_INVALID_ROOM_TYPE);
    }

    @Test
    void 그룹채팅방_생성_계정없음_예외() {
        // Given
        Long creatorId = 99L;
        GroupChatRoomCreateRequestDto request =
                createGroupRequest("테스트방", ChatRoomType.GROUP, null);

        when(accountRepository.findById(creatorId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> chatRoomService.createGroupRoom(creatorId, request))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 그룹채팅방_생성_락획득실패_예외() {
        // Given
        Long creatorId = 1L;
        GroupChatRoomCreateRequestDto request =
                createGroupRequest("테스트방", ChatRoomType.GROUP, null);

        doThrow(new BusinessException(ErrorCode.LOCK_ACQUIRE_FAILED))
                .when(redisLockService).executeWithLock(any(String.class), any(Duration.class), any(Supplier.class));

        // When & Then
        assertThatThrownBy(() -> chatRoomService.createGroupRoom(creatorId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCK_ACQUIRE_FAILED);
    }

    // ===================== inviteParticipants =====================

    @Test
    void 참여자_초대_신규_성공() {
        // Given
        Long inviterId = 1L;
        Long roomId = 10L;
        Account inviter = createAccount(inviterId, "홍길동");
        Account invitee = createAccount(2L, "이순신");
        ChatRoom room = createGroupRoom(roomId, inviter);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doNothing().when(chatAccessHelper).verifyRoomActive(room);
        when(chatAccessHelper.verifyParticipant(inviterId, roomId)).thenReturn(createActiveParticipant(room, inviter));
        doNothing().when(chatAccessHelper).verifyGroupRoom(room);
        when(accountRepository.findAllById(anyCollection())).thenReturn(List.of(invitee));
        when(chatParticipantRepository.findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, 2L))
                .thenReturn(Optional.empty());
        when(accountRepository.findById(inviterId)).thenReturn(Optional.of(inviter));

        // When
        chatRoomService.inviteParticipants(inviterId, roomId, List.of(2L));

        // Then
        verify(chatParticipantRepository).save(any(ChatParticipant.class));
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void 참여자_초대_LEFT상태_재입장_성공() {
        // Given
        Long inviterId = 1L;
        Long roomId = 10L;
        Account inviter = createAccount(inviterId, "홍길동");
        Account invitee = createAccount(2L, "이순신");
        ChatRoom room = createGroupRoom(roomId, inviter);
        ChatParticipant leftParticipant = ChatParticipant.create(room, invitee);
        leftParticipant.leave();

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doNothing().when(chatAccessHelper).verifyRoomActive(room);
        when(chatAccessHelper.verifyParticipant(inviterId, roomId)).thenReturn(createActiveParticipant(room, inviter));
        doNothing().when(chatAccessHelper).verifyGroupRoom(room);
        when(accountRepository.findAllById(anyCollection())).thenReturn(List.of(invitee));
        when(chatParticipantRepository.findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, 2L))
                .thenReturn(Optional.of(leftParticipant));
        when(accountRepository.findById(inviterId)).thenReturn(Optional.of(inviter));

        // When
        chatRoomService.inviteParticipants(inviterId, roomId, List.of(2L));

        // Then
        assertThat(leftParticipant.isActive()).isTrue(); // rejoin 호출 확인
        verify(chatParticipantRepository, never()).save(any(ChatParticipant.class)); // save 없이 rejoin
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void 참여자_초대_이미ACTIVE_시스템메시지없음() {
        // Given
        Long inviterId = 1L;
        Long roomId = 10L;
        Account inviter = createAccount(inviterId, "홍길동");
        Account invitee = createAccount(2L, "이순신");
        ChatRoom room = createGroupRoom(roomId, inviter);
        ChatParticipant activeParticipant = ChatParticipant.create(room, invitee);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doNothing().when(chatAccessHelper).verifyRoomActive(room);
        when(chatAccessHelper.verifyParticipant(inviterId, roomId)).thenReturn(createActiveParticipant(room, inviter));
        doNothing().when(chatAccessHelper).verifyGroupRoom(room);
        when(accountRepository.findAllById(anyCollection())).thenReturn(List.of(invitee));
        when(chatParticipantRepository.findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, 2L))
                .thenReturn(Optional.of(activeParticipant));

        // When
        chatRoomService.inviteParticipants(inviterId, roomId, List.of(2L));

        // Then
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
        verify(chatParticipantRepository, never()).save(any(ChatParticipant.class));
    }

    @Test
    void 참여자_초대_비활성방_예외() {
        // Given
        Long inviterId = 1L;
        Long roomId = 10L;
        Account inviter = createAccount(inviterId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, inviter);
        room.deactivate();

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doThrow(new BadRequestException(ErrorCode.CHAT_ROOM_INACTIVE))
                .when(chatAccessHelper).verifyRoomActive(room);

        // When & Then
        assertThatThrownBy(() -> chatRoomService.inviteParticipants(inviterId, roomId, List.of(2L)))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_INACTIVE);
    }

    @Test
    void 참여자_초대_비참여자_예외() {
        // Given
        Long inviterId = 1L;
        Long roomId = 10L;
        Account inviter = createAccount(inviterId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, inviter);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doNothing().when(chatAccessHelper).verifyRoomActive(room);
        doThrow(new ForbiddenException(ErrorCode.CHAT_NOT_PARTICIPANT))
                .when(chatAccessHelper).verifyParticipant(inviterId, roomId);

        // When & Then
        assertThatThrownBy(() -> chatRoomService.inviteParticipants(inviterId, roomId, List.of(2L)))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_NOT_PARTICIPANT);
    }

    @Test
    void 참여자_초대_PRIVATE방_예외() {
        // Given
        Long inviterId = 1L;
        Long roomId = 10L;
        Account inviter = createAccount(inviterId, "홍길동");
        ChatRoom room = ChatRoom.createGroup(inviter, ChatRoomType.PRIVATE, "PRIVATE방", ChatRoomRefType.NONE, null, null);
        ReflectionTestUtils.setField(room, "chatroomId", roomId);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doNothing().when(chatAccessHelper).verifyRoomActive(room);
        when(chatAccessHelper.verifyParticipant(inviterId, roomId)).thenReturn(createActiveParticipant(room, inviter));
        doThrow(new BadRequestException(ErrorCode.CHAT_NOT_GROUP_ROOM))
                .when(chatAccessHelper).verifyGroupRoom(room);

        // When & Then
        assertThatThrownBy(() -> chatRoomService.inviteParticipants(inviterId, roomId, List.of(2L)))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_NOT_GROUP_ROOM);
    }

    @Test
    void 참여자_초대_락획득실패_예외() {
        // Given
        Long inviterId = 1L;
        Long roomId = 10L;

        doThrow(new BusinessException(ErrorCode.LOCK_ACQUIRE_FAILED))
                .when(redisLockService).executeWithLock(any(String.class), any(Duration.class), any(Supplier.class));

        // When & Then
        assertThatThrownBy(() -> chatRoomService.inviteParticipants(inviterId, roomId, List.of(2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCK_ACQUIRE_FAILED);
    }

    // ===================== joinRoom =====================

    @Test
    void 채팅방_입장_신규참여자_성공() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, account);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doNothing().when(chatAccessHelper).verifyRoomActive(room);
        doNothing().when(chatAccessHelper).verifyGroupRoom(room);
        when(chatParticipantRepository.findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, accountId))
                .thenReturn(Optional.empty());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // When
        chatRoomService.joinRoom(accountId, roomId);

        // Then
        verify(chatParticipantRepository).save(any(ChatParticipant.class));
        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(eventPublisher).publishEvent(any(ChatRoomReadEvent.class));
    }

    @Test
    void 채팅방_입장_LEFT참여자_재입장_성공() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, account);
        ChatParticipant leftParticipant = ChatParticipant.create(room, account);
        leftParticipant.leave();

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doNothing().when(chatAccessHelper).verifyRoomActive(room);
        doNothing().when(chatAccessHelper).verifyGroupRoom(room);
        when(chatParticipantRepository.findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, accountId))
                .thenReturn(Optional.of(leftParticipant));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // When
        chatRoomService.joinRoom(accountId, roomId);

        // Then
        assertThat(leftParticipant.isActive()).isTrue(); // rejoin 호출 확인
        verify(chatParticipantRepository, never()).save(any(ChatParticipant.class));
        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(eventPublisher).publishEvent(any(ChatRoomReadEvent.class));
    }

    @Test
    void 채팅방_입장_이미ACTIVE_읽음처리만() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, account);
        ChatParticipant activeParticipant = ChatParticipant.create(room, account);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doNothing().when(chatAccessHelper).verifyRoomActive(room);
        doNothing().when(chatAccessHelper).verifyGroupRoom(room);
        when(chatParticipantRepository.findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, accountId))
                .thenReturn(Optional.of(activeParticipant));

        // When
        chatRoomService.joinRoom(accountId, roomId);

        // Then
        verify(chatUnreadService).resetRoom(accountId, roomId);
        verify(chatParticipantRepository, never()).save(any());
        verify(chatMessageRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 채팅방_입장_비활성방_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, account);
        room.deactivate();

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doThrow(new BadRequestException(ErrorCode.CHAT_ROOM_INACTIVE))
                .when(chatAccessHelper).verifyRoomActive(room);

        // When & Then
        assertThatThrownBy(() -> chatRoomService.joinRoom(accountId, roomId))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_INACTIVE);
    }

    @Test
    void 채팅방_입장_PRIVATE방_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, account);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doNothing().when(chatAccessHelper).verifyRoomActive(room);
        doThrow(new BadRequestException(ErrorCode.CHAT_NOT_GROUP_ROOM))
                .when(chatAccessHelper).verifyGroupRoom(room);

        // When & Then
        assertThatThrownBy(() -> chatRoomService.joinRoom(accountId, roomId))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_NOT_GROUP_ROOM);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 채팅방_입장_락획득실패_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;

        doThrow(new BusinessException(ErrorCode.LOCK_ACQUIRE_FAILED))
                .when(redisLockService).executeWithLock(any(String.class), any(Duration.class), any(Supplier.class));

        // When & Then
        assertThatThrownBy(() -> chatRoomService.joinRoom(accountId, roomId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCK_ACQUIRE_FAILED);
    }

    // ===================== leaveRoom =====================

    @Test
    void 채팅방_나가기_성공() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, account);
        ChatParticipant participant = createActiveParticipant(room, account);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        when(chatAccessHelper.verifyParticipant(accountId, roomId)).thenReturn(participant);
        when(chatParticipantRepository.countByChatRoom_ChatroomIdAndStatus(eq(roomId), eq(ParticipantStatus.ACTIVE)))
                .thenReturn(1L);

        // When
        chatRoomService.leaveRoom(accountId, roomId);

        // Then
        assertThat(participant.isActive()).isFalse();
        verify(eventPublisher).publishEvent(any(ChatRoomReadEvent.class));
        verify(chatMessageRepository).save(any(ChatMessage.class));
        assertThat(room.isActive()).isTrue(); // 다른 참여자가 남아 있으므로 방 유지
    }

    @Test
    void 채팅방_나가기_마지막참여자_방비활성화() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, account);
        ChatParticipant participant = createActiveParticipant(room, account);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        when(chatAccessHelper.verifyParticipant(accountId, roomId)).thenReturn(participant);
        when(chatParticipantRepository.countByChatRoom_ChatroomIdAndStatus(eq(roomId), eq(ParticipantStatus.ACTIVE)))
                .thenReturn(0L);

        // When
        chatRoomService.leaveRoom(accountId, roomId);

        // Then
        assertThat(room.isActive()).isFalse();
    }

    @Test
    void 채팅방_나가기_방없음_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 99L;

        doThrow(new NotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND))
                .when(chatAccessHelper).getRoomOrThrow(roomId);

        // When & Then
        assertThatThrownBy(() -> chatRoomService.leaveRoom(accountId, roomId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void 채팅방_나가기_비참여자_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, account);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doThrow(new ForbiddenException(ErrorCode.CHAT_NOT_PARTICIPANT))
                .when(chatAccessHelper).verifyParticipant(accountId, roomId);

        // When & Then
        assertThatThrownBy(() -> chatRoomService.leaveRoom(accountId, roomId))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_NOT_PARTICIPANT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 채팅방_나가기_락획득실패_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;

        doThrow(new BusinessException(ErrorCode.LOCK_ACQUIRE_FAILED))
                .when(redisLockService)
                .executeWithLock(
                        any(String.class),
                        any(Duration.class),
                        any(Supplier.class)
                );

        // When & Then
        assertThatThrownBy(() -> chatRoomService.leaveRoom(accountId, roomId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCK_ACQUIRE_FAILED);
    }

    // ===================== markRoomAsRead =====================

    @Test
    void 읽음처리_성공() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, account);
        ChatParticipant participant = createActiveParticipant(room, account);

        when(chatAccessHelper.verifyParticipant(accountId, roomId)).thenReturn(participant);

        // When
        chatRoomService.markRoomAsRead(accountId, roomId);

        // Then
        assertThat(participant.getLastReadTime()).isNotNull();
        verify(eventPublisher).publishEvent(any(ChatRoomReadEvent.class));
    }

    @Test
    void 읽음처리_비참여자_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;

        doThrow(new ForbiddenException(ErrorCode.CHAT_NOT_PARTICIPANT))
                .when(chatAccessHelper).verifyParticipant(accountId, roomId);

        // When & Then
        assertThatThrownBy(() -> chatRoomService.markRoomAsRead(accountId, roomId))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_NOT_PARTICIPANT);
    }
}
