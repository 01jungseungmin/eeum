package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.dto.request.ChatImageMessageSendRequestDto;
import com.eeum.eeum.application.chat.dto.request.ChatMessageSendRequestDto;
import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatUnreadCountResponseDto;
import com.eeum.eeum.application.chat.helper.ChatAccessHelper;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.eeum.eeum.domain.chat.event.ChatMessageBroadcastEvent;
import com.eeum.eeum.domain.chat.event.ChatMessageSentEvent;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatParticipantRepository chatParticipantRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatAccessHelper chatAccessHelper;
    @Mock private ChatUnreadService chatUnreadService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private StringRedisTemplate redisTemplate;
    @SuppressWarnings("unchecked")
    @Mock private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
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

    private ChatMessageSendRequestDto createTextRequest(String content, String clientMessageId) {
        ChatMessageSendRequestDto dto = new ChatMessageSendRequestDto();
        ReflectionTestUtils.setField(dto, "content", content);
        ReflectionTestUtils.setField(dto, "clientMessageId", clientMessageId);
        return dto;
    }

    private ChatImageMessageSendRequestDto createImageRequest(String imageUrl, String clientMessageId) {
        ChatImageMessageSendRequestDto dto = new ChatImageMessageSendRequestDto();
        ReflectionTestUtils.setField(dto, "imageUrl", imageUrl);
        ReflectionTestUtils.setField(dto, "clientMessageId", clientMessageId);
        return dto;
    }

    // ===================== sendMessage =====================

    @Test
    void 텍스트_메시지_발송_성공() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account sender = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, sender);
        ChatParticipant participant = ChatParticipant.create(room, sender);
        ChatMessageSendRequestDto request = createTextRequest("안녕하세요", null);

        when(chatAccessHelper.getRoomWithPessimisticLockOrThrow(roomId)).thenReturn(room);
        when(chatAccessHelper.verifyParticipant(accountId, roomId)).thenReturn(participant);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ChatMessageResponseDto result = chatMessageService.sendMessage(accountId, roomId, request);

        // Then
        assertThat(result).isNotNull();
        verify(chatAccessHelper).getRoomWithPessimisticLockOrThrow(roomId);
        verify(chatMessageRepository).save(any(ChatMessage.class));
        // room.updateLastMessageAt()는 엔티티 직접 호출 — repository 검증 없음
        verify(eventPublisher).publishEvent(any(ChatMessageBroadcastEvent.class));
        verify(eventPublisher).publishEvent(any(ChatMessageSentEvent.class));
    }

    @Test
    void 텍스트_메시지_발송_clientMessageId_있을때_멱등키_Redis_등록() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account sender = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, sender);
        ChatParticipant participant = ChatParticipant.create(room, sender);
        ChatMessageSendRequestDto request = createTextRequest("안녕하세요", "unique-uuid");

        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(chatAccessHelper.getRoomWithPessimisticLockOrThrow(roomId)).thenReturn(room);
        when(chatAccessHelper.verifyParticipant(accountId, roomId)).thenReturn(participant);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        chatMessageService.sendMessage(accountId, roomId, request);

        // Then
        verify(valueOps).setIfAbsent(anyString(), eq("1"), any(Duration.class));
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void 텍스트_메시지_발송_멱등성_중복요청_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        ChatMessageSendRequestDto request = createTextRequest("안녕하세요", "dup-uuid");

        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> chatMessageService.sendMessage(accountId, roomId, request))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_MESSAGE_DUPLICATE);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void 텍스트_메시지_발송_비참여자_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        ChatMessageSendRequestDto request = createTextRequest("안녕", null);

        doThrow(new ForbiddenException(ErrorCode.CHAT_NOT_PARTICIPANT))
                .when(chatAccessHelper).verifyParticipant(accountId, roomId);

        // When & Then
        assertThatThrownBy(() -> chatMessageService.sendMessage(accountId, roomId, request))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_NOT_PARTICIPANT);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void 텍스트_메시지_발송_비활성방_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account sender = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, sender);
        room.deactivate();
        ChatMessageSendRequestDto request = createTextRequest("안녕", null);

        when(chatAccessHelper.getRoomWithPessimisticLockOrThrow(roomId)).thenReturn(room);
        doThrow(new BadRequestException(ErrorCode.CHAT_ROOM_INACTIVE))
                .when(chatAccessHelper).verifyRoomActive(room);

        // When & Then
        assertThatThrownBy(() -> chatMessageService.sendMessage(accountId, roomId, request))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_INACTIVE);

        verify(chatMessageRepository, never()).save(any());
    }

    // ===================== sendImageMessage =====================

    @Test
    void 이미지_메시지_발송_성공() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account sender = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, sender);
        ChatParticipant participant = ChatParticipant.create(room, sender);
        ChatImageMessageSendRequestDto request = createImageRequest("https://cdn.example.com/img.jpg", null);

        when(chatAccessHelper.getRoomWithPessimisticLockOrThrow(roomId)).thenReturn(room);
        when(chatAccessHelper.verifyParticipant(accountId, roomId)).thenReturn(participant);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ChatMessageResponseDto result = chatMessageService.sendImageMessage(accountId, roomId, request);

        // Then
        assertThat(result).isNotNull();
        verify(chatAccessHelper).getRoomWithPessimisticLockOrThrow(roomId);
        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(eventPublisher).publishEvent(any(ChatMessageBroadcastEvent.class));
        verify(eventPublisher).publishEvent(any(ChatMessageSentEvent.class));
    }

    @Test
    void 이미지_메시지_발송_멱등성_중복요청_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        ChatImageMessageSendRequestDto request = createImageRequest("https://cdn.example.com/img.jpg", "dup-uuid");

        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> chatMessageService.sendImageMessage(accountId, roomId, request))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_MESSAGE_DUPLICATE);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void 이미지_메시지_발송_비참여자_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        ChatImageMessageSendRequestDto request = createImageRequest("https://cdn.example.com/img.jpg", null);

        doThrow(new ForbiddenException(ErrorCode.CHAT_NOT_PARTICIPANT))
                .when(chatAccessHelper).verifyParticipant(accountId, roomId);

        // When & Then
        assertThatThrownBy(() -> chatMessageService.sendImageMessage(accountId, roomId, request))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_NOT_PARTICIPANT);
    }

    @Test
    void 이미지_메시지_발송_비활성방_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account sender = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, sender);
        room.deactivate();
        ChatImageMessageSendRequestDto request = createImageRequest("https://cdn.example.com/img.jpg", null);

        when(chatAccessHelper.getRoomWithPessimisticLockOrThrow(roomId)).thenReturn(room);
        doThrow(new BadRequestException(ErrorCode.CHAT_ROOM_INACTIVE))
                .when(chatAccessHelper).verifyRoomActive(room);

        // When & Then
        assertThatThrownBy(() -> chatMessageService.sendImageMessage(accountId, roomId, request))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_INACTIVE);
    }

    // ===================== getMessages (커서 기반) =====================

    @Test
    void 메시지_목록_조회_첫_페이지_cursor_null() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        int size = 20;
        Account sender = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, sender);
        ChatParticipant participant = ChatParticipant.create(room, sender);
        ChatMessage message = ChatMessage.text(room, sender, "안녕하세요");

        when(chatAccessHelper.verifyParticipant(accountId, roomId)).thenReturn(participant);
        when(chatMessageRepository.findAllByChatRoom_ChatroomIdOrderBySentAtDesc(
                eq(roomId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(message)));

        // When
        Slice<ChatMessageResponseDto> result = chatMessageService.getMessages(accountId, roomId, null, size);

        // Then
        assertThat(result.getContent()).hasSize(1);
        verify(chatAccessHelper).verifyParticipant(accountId, roomId);
    }

    @Test
    void 메시지_목록_조회_cursor_있으면_이전_메시지_조회() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        int size = 20;
        LocalDateTime cursor = LocalDateTime.now().minusMinutes(10);
        Account sender = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, sender);
        ChatParticipant participant = ChatParticipant.create(room, sender);
        ChatMessage message = ChatMessage.text(room, sender, "이전 메시지");

        when(chatAccessHelper.verifyParticipant(accountId, roomId)).thenReturn(participant);
        when(chatMessageRepository.findAllByChatRoom_ChatroomIdAndSentAtBeforeOrderBySentAtDesc(
                eq(roomId), eq(cursor), any(PageRequest.class)))
                .thenReturn(List.of(message));

        // When
        Slice<ChatMessageResponseDto> result = chatMessageService.getMessages(accountId, roomId, cursor, size);

        // Then
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void 메시지_목록_조회_비참여자_예외() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;

        doThrow(new ForbiddenException(ErrorCode.CHAT_NOT_PARTICIPANT))
                .when(chatAccessHelper).verifyParticipant(accountId, roomId);

        // When & Then
        assertThatThrownBy(() -> chatMessageService.getMessages(accountId, roomId, null, 20))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_NOT_PARTICIPANT);
    }

    // ===================== countUnread =====================

    @Test
    void 전체_안읽음_수_조회_캐시히트() {
        // Given
        Long accountId = 1L;
        when(chatUnreadService.getTotalUnread(eq(accountId), any(LongSupplier.class))).thenReturn(5L);

        // When
        ChatUnreadCountResponseDto result = chatMessageService.countUnread(accountId);

        // Then
        assertThat(result.getUnreadCount()).isEqualTo(5L);
    }

    @Test
    void 전체_안읽음_수_조회_캐시미스_DB폴백() {
        // Given
        Long accountId = 1L;
        doAnswer(inv -> {
            LongSupplier fallback = inv.getArgument(1);
            return fallback.getAsLong();
        }).when(chatUnreadService).getTotalUnread(eq(accountId), any(LongSupplier.class));

        Account sender = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(10L, sender);
        ChatParticipant participant = ChatParticipant.create(room, sender);
        when(chatParticipantRepository.findActiveParticipationsInActiveRooms(accountId, ParticipantStatus.ACTIVE))
                .thenReturn(List.of(participant));
        when(chatMessageRepository.countByChatRoom_ChatroomIdAndSentAtAfterAndAccount_AccountIdNot(
                eq(10L), any(LocalDateTime.class), eq(accountId)))
                .thenReturn(3L);

        // When
        ChatUnreadCountResponseDto result = chatMessageService.countUnread(accountId);

        // Then
        assertThat(result.getUnreadCount()).isEqualTo(3L);
        verify(chatParticipantRepository).findActiveParticipationsInActiveRooms(accountId, ParticipantStatus.ACTIVE);
        verify(chatMessageRepository).countByChatRoom_ChatroomIdAndSentAtAfterAndAccount_AccountIdNot(
                eq(10L), any(LocalDateTime.class), eq(accountId));
    }

    // ===================== deleteMessage =====================

    @Test
    void 메시지_삭제_성공_후_브로드캐스트_이벤트_발행() {
        // Given
        Long accountId = 1L;
        Long messageId = 100L;
        Account sender = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(10L, sender);
        ChatMessage message = ChatMessage.text(room, sender, "삭제할 메시지");

        when(chatAccessHelper.verifyMessageOwnership(accountId, messageId)).thenReturn(message);

        // When
        chatMessageService.deleteMessage(accountId, messageId);

        // Then
        assertThat(message.isDeleted()).isTrue();
        verify(eventPublisher).publishEvent(any(ChatMessageBroadcastEvent.class));
    }

    @Test
    void 메시지_삭제_본인메시지아님_예외() {
        // Given
        Long accountId = 1L;
        Long messageId = 100L;

        doThrow(new ForbiddenException(ErrorCode.CHAT_MESSAGE_ACCESS_DENIED))
                .when(chatAccessHelper).verifyMessageOwnership(accountId, messageId);

        // When & Then
        assertThatThrownBy(() -> chatMessageService.deleteMessage(accountId, messageId))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_MESSAGE_ACCESS_DENIED);
    }

    @Test
    void 메시지_삭제_이미삭제된메시지_예외() {
        // Given
        Long accountId = 1L;
        Long messageId = 100L;
        Account sender = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(10L, sender);
        ChatMessage message = ChatMessage.text(room, sender, "이미 삭제된 메시지");
        message.markDeleted();

        when(chatAccessHelper.verifyMessageOwnership(accountId, messageId)).thenReturn(message);

        // When & Then
        assertThatThrownBy(() -> chatMessageService.deleteMessage(accountId, messageId))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_MESSAGE_NOT_DELETABLE);
    }

    @Test
    void 메시지_삭제_시스템메시지_예외() {
        // Given
        Long accountId = 1L;
        Long messageId = 100L;
        Account sender = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(10L, sender);
        ChatMessage message = ChatMessage.system(room, sender, "시스템 메시지");

        when(chatAccessHelper.verifyMessageOwnership(accountId, messageId)).thenReturn(message);

        // When & Then
        assertThatThrownBy(() -> chatMessageService.deleteMessage(accountId, messageId))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_MESSAGE_NOT_DELETABLE);
    }
}
