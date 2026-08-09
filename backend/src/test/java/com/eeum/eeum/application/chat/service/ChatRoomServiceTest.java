package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.dto.request.GroupChatRoomCreateRequestDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomResponseDto;
import com.eeum.eeum.application.chat.helper.ChatAccessHelper;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.eeum.eeum.domain.chat.event.ChatRoomClosedEvent;
import com.eeum.eeum.domain.chat.event.ChatRoomReadEvent;
import com.eeum.eeum.domain.chat.event.ChatRoomUnreadBulkResetEvent;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
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
import static org.mockito.Mockito.doReturn;
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
    @Mock private StoreRepository storeRepository;
    @Mock private AccountRegionRepository accountRegionRepository;
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

        // 상태 변경 경로는 일반 조회로 얻은 테스트 방을 DB 잠금 조회에서도 그대로 반환한다.
        lenient().when(chatAccessHelper.getRoomWithPessimisticLockOrThrow(any(Long.class)))
                .thenAnswer(inv -> chatAccessHelper.getRoomOrThrow(inv.getArgument(0)));
        lenient().when(storeRepository.findByIdWithPessimisticLock(any(Long.class)))
                .thenAnswer(inv -> storeRepository.findById(inv.getArgument(0)));
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

    private Region createRegion(Long id, String code) {
        Region region = Region.create(code, "서울특별시", "강남구", "역삼동", 3000);
        ReflectionTestUtils.setField(region, "regionId", id);
        return region;
    }

    private AccountRegion createVerifiedPrimaryRegion(
            Account account, Region region, Long accountRegionId) {
        AccountRegion accountRegion = AccountRegion.create(account, region);
        ReflectionTestUtils.setField(accountRegion, "accountRegionId", accountRegionId);
        accountRegion.verify();
        account.setPrimaryRegion(accountRegionId);
        return accountRegion;
    }

    private ChatRoom createPublicGroupRoom(Long id, Account creator, Region region) {
        ChatRoom room = ChatRoom.createGroup(
                creator, ChatRoomType.GROUP, "테스트방", ChatRoomRefType.NONE, null, region);
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
        when(chatRoomRepository.saveAndFlush(any(ChatRoom.class))).thenAnswer(inv -> inv.getArgument(0));
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
        verify(chatRoomRepository).saveAndFlush(any(ChatRoom.class));
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
        when(chatRoomRepository.saveAndFlush(any(ChatRoom.class))).thenAnswer(inv -> inv.getArgument(0));
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
    void 가게_단톡방은_refId가_없으면_생성할_수_없다() {
        // Given
        GroupChatRoomCreateRequestDto request = createStoreRoomRequest();
        ReflectionTestUtils.setField(request, "refId", null);

        // When & Then
        assertThatThrownBy(() -> chatRoomService.createGroupRoom(1L, request))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_INVALID_REF_ID);
        verify(redisLockService, never())
                .executeWithLock(any(String.class), any(Duration.class), any(Supplier.class));
        verify(storeRepository, never()).findByIdWithPessimisticLock(anyLong());
    }

    @Test
    void 가게_단톡방은_refId가_양수가_아니면_생성할_수_없다() {
        // Given
        GroupChatRoomCreateRequestDto request = createStoreRoomRequest();
        ReflectionTestUtils.setField(request, "refId", 0L);

        // When & Then
        assertThatThrownBy(() -> chatRoomService.createGroupRoom(1L, request))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_INVALID_REF_ID);
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
    void 비STORE_그룹채팅방_생성은_락을_잡지_않는다() {
        // Given: 비STORE 방은 중복 판정이 없어 락이 중복을 막지 못한다.
        //        대기 없는 락 정책 탓에 정상 동시 요청만 LOCK_ACQUIRE_FAILED로 실패시키므로 락을 제거했다.
        Long creatorId = 1L;
        Account creator = createAccount(creatorId, "홍길동");
        GroupChatRoomCreateRequestDto request = createGroupRequest("테스트방", ChatRoomType.GROUP, null);

        when(accountRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(chatRoomRepository.saveAndFlush(any(ChatRoom.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatParticipantRepository.save(any(ChatParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatParticipantRepository.countByChatRoom_ChatroomIdAndStatus(any(), eq(ParticipantStatus.ACTIVE)))
                .thenReturn(1L);
        when(chatUnreadService.getRoomUnread(any(), any(), any())).thenReturn(0L);
        when(chatMessageRepository.findFirstByChatRoom_ChatroomIdOrderBySentAtDesc(any()))
                .thenReturn(Optional.empty());

        // When
        ChatRoomResponseDto result = chatRoomService.createGroupRoom(creatorId, request);

        // Then: 트랜잭션만 열고 분산 락은 전혀 획득하지 않는다
        assertThat(result).isNotNull();
        verify(redisLockService, never())
                .executeWithLock(any(String.class), any(Duration.class), any(Supplier.class));
        verify(transactionTemplate).execute(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void 가게_단톡방_생성은_여전히_락을_잡고_실패_시_예외가_전파된다() {
        // Given: STORE 방은 "가게당 ACTIVE 1개" 멱등 규칙이 있어 조회~생성 구간을 직렬화해야 한다
        Long ownerId = 1L;

        doThrow(new BusinessException(ErrorCode.LOCK_ACQUIRE_FAILED))
                .when(redisLockService).executeWithLock(any(String.class), any(Duration.class), any(Supplier.class));

        // When & Then
        assertThatThrownBy(() -> chatRoomService.createGroupRoom(ownerId, createStoreRoomRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCK_ACQUIRE_FAILED);
    }

    // ===================== 가게 단톡방 생성 / 종료 / 재생성 =====================

    private static final Long STORE_ID = 500L;

    private Store createStore(Account owner) {
        Store store = Store.createForOwnerSignup(owner, "테스트 상점", "서울시", "010-0000-0000");
        ReflectionTestUtils.setField(store, "storeId", STORE_ID);
        return store;
    }

    private GroupChatRoomCreateRequestDto createStoreRoomRequest() {
        GroupChatRoomCreateRequestDto dto = new GroupChatRoomCreateRequestDto();
        ReflectionTestUtils.setField(dto, "name", null);
        ReflectionTestUtils.setField(dto, "type", ChatRoomType.GROUP);
        ReflectionTestUtils.setField(dto, "refType", ChatRoomRefType.STORE);
        ReflectionTestUtils.setField(dto, "refId", STORE_ID);
        ReflectionTestUtils.setField(dto, "participantAccountIds", null);
        return dto;
    }

    private ChatRoom createStoreRoom(Long id, Account creator) {
        ChatRoom room = ChatRoom.createGroup(
                creator, ChatRoomType.GROUP, "테스트 상점 단톡방",
                ChatRoomRefType.STORE, STORE_ID, null);
        ReflectionTestUtils.setField(room, "chatroomId", id);
        return room;
    }

    private ChatRoom createStoreRoom(Long id, Account creator, Region region) {
        ChatRoom room = ChatRoom.createGroup(
                creator, ChatRoomType.GROUP, "테스트 상점 단톡방",
                ChatRoomRefType.STORE, STORE_ID, region);
        ReflectionTestUtils.setField(room, "chatroomId", id);
        return room;
    }

    private void stubRoomResponseLookups() {
        when(chatParticipantRepository.countByChatRoom_ChatroomIdAndStatus(any(), eq(ParticipantStatus.ACTIVE)))
                .thenReturn(1L);
        when(chatUnreadService.getRoomUnread(any(), any(), any())).thenReturn(0L);
        when(chatMessageRepository.findFirstByChatRoom_ChatroomIdOrderBySentAtDesc(any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void 가게_단톡방_생성_시_ACTIVE_방이_있으면_기존_방을_반환한다() {
        // Given
        Long ownerId = 1L;
        Account owner = createAccount(ownerId, "사장님");
        ChatRoom existing = createStoreRoom(10L, owner);

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(createStore(owner)));
        when(chatRoomRepository.findFirstByRefTypeAndRefIdAndIsActiveTrueOrderByChatroomIdDesc(
                ChatRoomRefType.STORE, STORE_ID))
                .thenReturn(Optional.of(existing));
        stubRoomResponseLookups();

        // When
        ChatRoomResponseDto result = chatRoomService.createGroupRoom(ownerId, createStoreRoomRequest());

        // Then: 새 방을 만들지 않고 기존 ACTIVE 방을 그대로 돌려준다 (멱등)
        assertThat(result.getRoomId()).isEqualTo(10L);
        verify(chatRoomRepository, never()).saveAndFlush(any(ChatRoom.class));
    }

    @Test
    void 종료된_단톡방만_있으면_새_roomId로_재생성된다() {
        // Given: 이전 방(10L)은 종료됨 → ACTIVE 조회 결과가 비어 있다
        Long ownerId = 1L;
        Account owner = createAccount(ownerId, "사장님");

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(createStore(owner)));
        when(chatRoomRepository.findFirstByRefTypeAndRefIdAndIsActiveTrueOrderByChatroomIdDesc(
                ChatRoomRefType.STORE, STORE_ID))
                .thenReturn(Optional.empty());
        when(accountRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(chatRoomRepository.saveAndFlush(any(ChatRoom.class))).thenAnswer(inv -> {
            ChatRoom saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "chatroomId", 20L); // DB가 새 PK를 발급한 상황
            return saved;
        });
        when(chatParticipantRepository.save(any(ChatParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        stubRoomResponseLookups();

        // When
        ChatRoomResponseDto result = chatRoomService.createGroupRoom(ownerId, createStoreRoomRequest());

        // Then: 종료된 옛 방(10L)이 아니라 새로 발급된 roomId를 반환한다
        assertThat(result.getRoomId()).isEqualTo(20L);
        assertThat(result.getRoomId()).isNotEqualTo(10L);
        assertThat(result.isActive()).isTrue();
        verify(chatRoomRepository).saveAndFlush(any(ChatRoom.class));
    }

    @Test
    void 동시_생성으로_ACTIVE_방_유니크가_위반되면_409로_거부된다() {
        // Given: Redis 락이 유실돼 두 요청이 동시에 INSERT를 시도한 상황을 DB 제약이 잡아낸다
        Long ownerId = 1L;
        Account owner = createAccount(ownerId, "사장님");

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(createStore(owner)));
        when(chatRoomRepository.findFirstByRefTypeAndRefIdAndIsActiveTrueOrderByChatroomIdDesc(
                ChatRoomRefType.STORE, STORE_ID))
                .thenReturn(Optional.empty());
        when(accountRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(chatRoomRepository.saveAndFlush(any(ChatRoom.class)))
                .thenThrow(new DataIntegrityViolationException("uk_chat_room_active_ref"));

        // When & Then
        assertThatThrownBy(() -> chatRoomService.createGroupRoom(ownerId, createStoreRoomRequest()))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_ALREADY_EXISTS);
    }

    @Test
    void 가게_소유자가_아니면_단톡방을_생성할_수_없다() {
        // Given
        Long otherAccountId = 2L;
        Account owner = createAccount(1L, "사장님");

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(createStore(owner)));

        // When & Then
        assertThatThrownBy(() -> chatRoomService.createGroupRoom(otherAccountId, createStoreRoomRequest()))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_ACCESS_DENIED);
    }

    // ===================== closeRoom =====================

    @Test
    void 사장이_가게_단톡방을_종료하면_비활성화되고_참여자_unread가_리셋된다() {
        // Given
        Long ownerId = 1L;
        Long roomId = 10L;
        Account owner = createAccount(ownerId, "사장님");
        ChatRoom room = createStoreRoom(roomId, owner);

        // 종료는 조건부 벌크 UPDATE로 수행되고(1건 영향), 이후 방을 다시 읽는다
        ReflectionTestUtils.setField(room, "isActive", false);
        ReflectionTestUtils.setField(room, "closedAt", LocalDateTime.now());
        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doReturn(Optional.of(createStore(owner)))
                .when(storeRepository).findByIdWithPessimisticLock(STORE_ID);
        when(chatRoomRepository.closeIfActive(eq(roomId), any(LocalDateTime.class))).thenReturn(1);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(createStore(owner)));
        when(accountRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatParticipantRepository.findActiveAccountIds(roomId)).thenReturn(List.of(1L, 2L, 3L));

        // When
        chatRoomService.closeRoom(ownerId, roomId);

        // Then: 물리 삭제가 아니라 조건부 상태 전이로 처리된다
        verify(chatRoomRepository).closeIfActive(eq(roomId), any(LocalDateTime.class));
        verify(chatMessageRepository).save(any(ChatMessage.class)); // 종료 SYSTEM 메시지

        // 참여자 3명의 unread는 이벤트 3건이 아니라 일괄 이벤트 1건으로 회수한다
        // (인원수만큼 @Async 작업과 DB UPDATE가 쌓이지 않도록)
        ArgumentCaptor<ChatRoomUnreadBulkResetEvent> bulkCaptor =
                ArgumentCaptor.forClass(ChatRoomUnreadBulkResetEvent.class);
        verify(eventPublisher).publishEvent(bulkCaptor.capture());
        assertThat(bulkCaptor.getValue().accountIds()).containsExactly(1L, 2L, 3L);
        verify(eventPublisher, never()).publishEvent(any(ChatRoomReadEvent.class));

        // 종료 통지에는 브로드캐스트 시각이 아니라 DB에 기록된 실제 종료 시각이 실린다
        ArgumentCaptor<ChatRoomClosedEvent> closedCaptor =
                ArgumentCaptor.forClass(ChatRoomClosedEvent.class);
        verify(eventPublisher).publishEvent(closedCaptor.capture());
        assertThat(closedCaptor.getValue().closedAt()).isEqualTo(room.getClosedAt());
        assertThat(closedCaptor.getValue().closedByAccountId()).isEqualTo(ownerId);
    }

    @Test
    void 가게_소유자가_아니면_단톡방을_종료할_수_없다() {
        // Given
        Long ownerId = 1L;
        Long otherAccountId = 2L;
        Long roomId = 10L;
        Account owner = createAccount(ownerId, "사장님");
        ChatRoom room = createStoreRoom(roomId, owner);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(createStore(owner)));

        // When & Then
        assertThatThrownBy(() -> chatRoomService.closeRoom(otherAccountId, roomId))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_ACCESS_DENIED);
        assertThat(room.isActive()).isTrue();
    }

    @Test
    void 일반_그룹방은_생성자만_종료할_수_있다() {
        // Given
        Long creatorId = 1L;
        Long otherAccountId = 2L;
        Long roomId = 10L;
        ChatRoom room = createGroupRoom(roomId, createAccount(creatorId, "홍길동"));

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);

        // When & Then
        assertThatThrownBy(() -> chatRoomService.closeRoom(otherAccountId, roomId))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_CLOSE_DENIED);
        assertThat(room.isActive()).isTrue();
        verify(redisLockService).executeWithLock(
                eq(LockKeys.chatRoomLeave(roomId)),
                any(Duration.class),
                any(Supplier.class));
    }

    @Test
    void 이미_종료된_방을_다시_종료해도_예외없이_무시된다() {
        // Given
        Long creatorId = 1L;
        Long roomId = 10L;
        Account creator = createAccount(creatorId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, creator);
        room.deactivate();

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);

        // When
        chatRoomService.closeRoom(creatorId, roomId);

        // Then: 멱등 — SYSTEM 메시지도 종료 이벤트도 중복 발행하지 않는다
        assertThat(room.isActive()).isFalse();
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
        verify(eventPublisher, never()).publishEvent(any(ChatRoomClosedEvent.class));
    }

    @Test
    void 관리자_강제_종료는_권한_검증_없이_동일한_정리_절차를_거친다() {
        // Given: 관리자는 가게 소유자가 아니어도 종료할 수 있어야 한다
        Long ownerId = 1L;
        Long roomId = 10L;
        Account owner = createAccount(ownerId, "사장님");
        ChatRoom room = createStoreRoom(roomId, owner);

        ReflectionTestUtils.setField(room, "isActive", false);
        ReflectionTestUtils.setField(room, "closedAt", LocalDateTime.now());
        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doReturn(Optional.of(createStore(owner)))
                .when(storeRepository).findByIdWithPessimisticLock(STORE_ID);
        when(chatRoomRepository.closeIfActive(eq(roomId), any(LocalDateTime.class))).thenReturn(1);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatParticipantRepository.findActiveAccountIds(roomId)).thenReturn(List.of(1L, 2L));

        // When
        chatRoomService.forceCloseRoom(roomId);

        // Then: 소유권 검증은 생략하지만 재생성과 직렬화하기 위한 가게행 잠금은 공유한다
        verify(chatRoomRepository).closeIfActive(eq(roomId), any(LocalDateTime.class));
        verify(storeRepository).findByIdWithPessimisticLock(STORE_ID);
        verify(eventPublisher).publishEvent(any(ChatRoomUnreadBulkResetEvent.class));

        ArgumentCaptor<ChatRoomClosedEvent> captor = ArgumentCaptor.forClass(ChatRoomClosedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().closedByAccountId())
                .as("관리자 종료는 actor 계정이 없으므로 null")
                .isNull();
    }

    @Test
    void 전원_퇴장으로_자동_종료될_때도_종료_통지가_발행된다() {
        // Given: 마지막 참여자 퇴장 → 자동 비활성화
        Long accountId = 1L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "홍길동");
        ChatRoom room = createGroupRoom(roomId, account);
        ChatParticipant participant = createActiveParticipant(room, account);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        when(chatAccessHelper.verifyParticipant(accountId, roomId)).thenReturn(participant);
        when(chatParticipantRepository.countByChatRoom_ChatroomIdAndStatus(eq(roomId), eq(ParticipantStatus.ACTIVE)))
                .thenReturn(0L);
        when(chatRoomRepository.closeIfActive(eq(roomId), any(LocalDateTime.class))).thenReturn(1);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        chatRoomService.leaveRoom(accountId, roomId);

        // Then: 어느 종료 경로든 동일한 뒷정리를 거친다
        verify(chatRoomRepository).closeIfActive(eq(roomId), any(LocalDateTime.class));
        verify(eventPublisher).publishEvent(any(ChatRoomClosedEvent.class));
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

        // Then: 중복 INSERT를 DB 유니크로 잡기 위해 saveAndFlush로 즉시 반영한다
        verify(chatParticipantRepository).saveAndFlush(any(ChatParticipant.class));
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
        when(chatAccessHelper.getRoomOrThrow(roomId))
                .thenReturn(createGroupRoom(roomId, createAccount(inviterId, "홍길동")));

        doThrow(new BusinessException(ErrorCode.LOCK_ACQUIRE_FAILED))
                .when(redisLockService).executeWithLock(any(String.class), any(Duration.class), any(Supplier.class));

        // When & Then
        assertThatThrownBy(() -> chatRoomService.inviteParticipants(inviterId, roomId, List.of(2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCK_ACQUIRE_FAILED);
        verify(redisLockService).executeWithLock(
                eq(LockKeys.chatRoomLeave(roomId)),
                any(Duration.class),
                any(Supplier.class));
    }

    // ===================== joinRoom =====================

    @Test
    void 채팅방_입장_신규참여자_성공() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "홍길동");
        Region region = createRegion(100L, "1168010100");
        AccountRegion accountRegion = createVerifiedPrimaryRegion(account, region, 1000L);
        ChatRoom room = createPublicGroupRoom(roomId, account, region);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doNothing().when(chatAccessHelper).verifyRoomActive(room);
        doNothing().when(chatAccessHelper).verifyGroupRoom(room);
        when(chatParticipantRepository.findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, accountId))
                .thenReturn(Optional.empty());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRegionRepository.findByAccountRegionIdAndAccount_AccountId(1000L, accountId))
                .thenReturn(Optional.of(accountRegion));

        // When
        chatRoomService.joinRoom(accountId, roomId);

        // Then: 중복 INSERT를 DB 유니크로 잡기 위해 saveAndFlush로 즉시 반영한다
        verify(chatParticipantRepository).saveAndFlush(any(ChatParticipant.class));
        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(eventPublisher).publishEvent(any(ChatRoomReadEvent.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 가게_단톡방도_공개목록에서_직접입장할_수_있다() {
        // Given
        Long ownerId = 1L;
        Long customerId = 2L;
        Long roomId = 10L;
        Account owner = createAccount(ownerId, "사장님");
        Account customer = createAccount(customerId, "고객");
        Region region = createRegion(100L, "1168010100");
        AccountRegion customerRegion = createVerifiedPrimaryRegion(customer, region, 1000L);
        ChatRoom room = createStoreRoom(roomId, owner, region);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(createStore(owner)));
        doNothing().when(chatAccessHelper).verifyRoomActive(room);
        doNothing().when(chatAccessHelper).verifyGroupRoom(room);
        when(chatParticipantRepository.findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, customerId))
                .thenReturn(Optional.empty());
        when(accountRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(accountRegionRepository.findByAccountRegionIdAndAccount_AccountId(1000L, customerId))
                .thenReturn(Optional.of(customerRegion));

        // When
        chatRoomService.joinRoom(customerId, roomId);

        // Then
        verify(redisLockService).executeWithLock(
                eq(LockKeys.chatRoomStore(STORE_ID)),
                any(Duration.class),
                any(Supplier.class));
        verify(storeRepository).findByIdWithPessimisticLock(STORE_ID);
        verify(chatAccessHelper).getRoomWithPessimisticLockOrThrow(roomId);
        verify(chatParticipantRepository).saveAndFlush(any(ChatParticipant.class));
        verify(eventPublisher).publishEvent(any(ChatRoomReadEvent.class));
    }

    @Test
    void 채팅방_입장_LEFT참여자_재입장_성공() {
        // Given
        Long accountId = 1L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "홍길동");
        Region region = createRegion(100L, "1168010100");
        AccountRegion accountRegion = createVerifiedPrimaryRegion(account, region, 1000L);
        ChatRoom room = createPublicGroupRoom(roomId, account, region);
        ChatParticipant leftParticipant = ChatParticipant.create(room, account);
        leftParticipant.leave();

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doNothing().when(chatAccessHelper).verifyRoomActive(room);
        doNothing().when(chatAccessHelper).verifyGroupRoom(room);
        when(chatParticipantRepository.findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, accountId))
                .thenReturn(Optional.of(leftParticipant));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRegionRepository.findByAccountRegionIdAndAccount_AccountId(1000L, accountId))
                .thenReturn(Optional.of(accountRegion));

        // When
        chatRoomService.joinRoom(accountId, roomId);

        // Then
        assertThat(leftParticipant.isActive()).isTrue(); // rejoin 호출 확인
        verify(chatParticipantRepository, never()).save(any(ChatParticipant.class));
        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(eventPublisher).publishEvent(any(ChatRoomReadEvent.class));
    }

    @Test
    void 채팅방_입장_타지역_사용자_CHAT_ROOM_ACCESS_DENIED() {
        // Given
        Long accountId = 2L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "타지역 사용자");
        Region roomRegion = createRegion(100L, "1168010100");
        Region accountRegionValue = createRegion(200L, "2644010100");
        AccountRegion accountRegion = createVerifiedPrimaryRegion(account, accountRegionValue, 2000L);
        ChatRoom room = createPublicGroupRoom(roomId, createAccount(1L, "방장"), roomRegion);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doNothing().when(chatAccessHelper).verifyRoomActive(room);
        doNothing().when(chatAccessHelper).verifyGroupRoom(room);
        when(chatParticipantRepository.findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, accountId))
                .thenReturn(Optional.empty());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRegionRepository.findByAccountRegionIdAndAccount_AccountId(2000L, accountId))
                .thenReturn(Optional.of(accountRegion));

        // When & Then
        assertThatThrownBy(() -> chatRoomService.joinRoom(accountId, roomId))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        verify(chatParticipantRepository, never()).saveAndFlush(any(ChatParticipant.class));
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
        verify(eventPublisher, never()).publishEvent(any(ChatRoomReadEvent.class));
    }

    @Test
    void 채팅방_입장_대표지역이_없으면_CHAT_ROOM_ACCESS_DENIED() {
        // Given
        Long accountId = 2L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "지역 미설정 사용자");
        Region roomRegion = createRegion(100L, "1168010100");
        ChatRoom room = createPublicGroupRoom(roomId, createAccount(1L, "방장"), roomRegion);

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doNothing().when(chatAccessHelper).verifyRoomActive(room);
        doNothing().when(chatAccessHelper).verifyGroupRoom(room);
        when(chatParticipantRepository.findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, accountId))
                .thenReturn(Optional.empty());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // When & Then
        assertThatThrownBy(() -> chatRoomService.joinRoom(accountId, roomId))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        verify(accountRegionRepository, never())
                .findByAccountRegionIdAndAccount_AccountId(anyLong(), anyLong());
        verify(chatParticipantRepository, never()).saveAndFlush(any(ChatParticipant.class));
    }

    @Test
    void 채팅방_입장_방에_공개지역이_없으면_CHAT_ROOM_ACCESS_DENIED() {
        // Given
        Long accountId = 2L;
        Long roomId = 10L;
        Account account = createAccount(accountId, "고객");
        Region accountRegionValue = createRegion(100L, "1168010100");
        createVerifiedPrimaryRegion(account, accountRegionValue, 1000L);
        ChatRoom room = createGroupRoom(roomId, createAccount(1L, "방장"));

        when(chatAccessHelper.getRoomOrThrow(roomId)).thenReturn(room);
        doNothing().when(chatAccessHelper).verifyRoomActive(room);
        doNothing().when(chatAccessHelper).verifyGroupRoom(room);
        when(chatParticipantRepository.findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, accountId))
                .thenReturn(Optional.empty());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // When & Then
        assertThatThrownBy(() -> chatRoomService.joinRoom(accountId, roomId))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        verify(accountRegionRepository, never())
                .findByAccountRegionIdAndAccount_AccountId(anyLong(), anyLong());
        verify(chatParticipantRepository, never()).saveAndFlush(any(ChatParticipant.class));
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
        when(chatAccessHelper.getRoomOrThrow(roomId))
                .thenReturn(createGroupRoom(roomId, createAccount(accountId, "홍길동")));

        doThrow(new BusinessException(ErrorCode.LOCK_ACQUIRE_FAILED))
                .when(redisLockService).executeWithLock(any(String.class), any(Duration.class), any(Supplier.class));

        // When & Then
        assertThatThrownBy(() -> chatRoomService.joinRoom(accountId, roomId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCK_ACQUIRE_FAILED);
        verify(redisLockService).executeWithLock(
                eq(LockKeys.chatRoomLeave(roomId)),
                any(Duration.class),
                any(Supplier.class));
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
        when(chatRoomRepository.closeIfActive(eq(roomId), any(LocalDateTime.class))).thenReturn(1);

        // Then: 마지막 참여자 퇴장 시 조건부 종료가 수행된다
        chatRoomService.leaveRoom(accountId, roomId);
        verify(chatRoomRepository).closeIfActive(eq(roomId), any(LocalDateTime.class));
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
        // 종료와 동일한 락 키를 고르기 위해 락 획득 전에 방을 한 번 읽는다
        when(chatAccessHelper.getRoomOrThrow(roomId))
                .thenReturn(createGroupRoom(roomId, createAccount(accountId, "홍길동")));

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
