package com.eeum.eeum.application.chat.service;

import com.eeum.eeum.application.chat.dto.request.GroupChatRoomCreateRequestDto;
import com.eeum.eeum.application.chat.dto.response.ChatParticipantResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatMessageResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatReadResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomDetailResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomPublicResponseDto;
import com.eeum.eeum.application.chat.dto.response.ChatRoomResponseDto;
import com.eeum.eeum.application.chat.helper.ChatAccessHelper;
import com.eeum.eeum.application.chat.helper.ChatMessagePreview;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.AccountRegion;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRegionRepository;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.chat.entity.ChatMessage;
import com.eeum.eeum.domain.chat.entity.ChatParticipant;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.domain.chat.enums.ParticipantStatus;
import com.eeum.eeum.domain.chat.repository.ChatMessageRepository;
import com.eeum.eeum.domain.chat.repository.ChatParticipantRepository;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.chat.event.ChatMessageBroadcastEvent;
import com.eeum.eeum.domain.chat.event.ChatRoomReadEvent;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AccountRepository accountRepository;
    private final AccountRegionRepository accountRegionRepository;
    private final ChatAccessHelper chatAccessHelper;
    private final ChatUnreadService chatUnreadService;
    private final RedisLockService redisLockService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final StoreRepository storeRepository;

    // ===================== 채팅방 생성 =====================

    // GROUP(단톡방) 채팅방 생성 + 참여자 일괄 초대
    // 락 획득 후 트랜잭션 시작 → 커밋 완료 후 락 해제 (가게 단톡방 중복 생성 방지)
    public ChatRoomResponseDto createGroupRoom(Long accountId, GroupChatRoomCreateRequestDto request) {
        ChatRoomType type = request.getType() != null ? request.getType() : ChatRoomType.GROUP;
        if (type == ChatRoomType.PRIVATE) {
            throw new BadRequestException(ErrorCode.CHAT_INVALID_ROOM_TYPE);
        }

        ChatRoomRefType refType = request.getRefType() != null ? request.getRefType() : ChatRoomRefType.NONE;
        boolean isStoreRoom = refType == ChatRoomRefType.STORE && request.getRefId() != null;

        // STORE 타입이면 storeId 기반 락으로 동일 가게 중복 생성 방지
        String lockKey = isStoreRoom
                ? LockKeys.chatRoomStore(request.getRefId())
                : LockKeys.chatRoom(accountId);

        return withLockAndTx(lockKey, () -> {
            Store store = null;
            if (isStoreRoom) {
                store = storeRepository.findById(request.getRefId())
                        .orElseThrow(() -> new NotFoundException(ErrorCode.STORE_NOT_FOUND));
                if (!store.isOwnedBy(accountId)) {
                    throw new ForbiddenException(ErrorCode.STORE_ACCESS_DENIED);
                }
                Optional<ChatRoom> existing = chatRoomRepository
                        .findByRefTypeAndRefIdAndType(ChatRoomRefType.STORE, request.getRefId(), type);
                if (existing.isPresent()) {
                    log.info("가게 단톡방 이미 존재 → 기존 방 반환: roomId={}, storeId={}",
                            existing.get().getChatroomId(), request.getRefId());
                    return toResponseDto(existing.get(), accountId);
                }
            }

            Account creator = getAccount(accountId);
            Region creatorRegion = getCreatorRegionOrNull(creator);
            ChatRoom room = ChatRoom.createGroup(creator, type,
                    resolveRoomName(request.getName(), isStoreRoom, store), refType, request.getRefId(),
                    creatorRegion);
            chatRoomRepository.save(room);
            chatParticipantRepository.save(ChatParticipant.create(room, creator));

            List<Account> invitees = loadInvitees(request.getParticipantAccountIds(), accountId);
            for (Account invitee : invitees) {
                chatParticipantRepository.save(ChatParticipant.create(room, invitee));
            }

            saveAndBroadcastSystemMessage(room, creator,
                    String.format("%s님이 채팅방을 개설했습니다.", creator.getName()));
            log.info("그룹 채팅방 생성: roomId={}, creator={}, 초대={}명",
                    room.getChatroomId(), accountId, invitees.size());
            return toResponseDto(room, accountId);
        });
    }

    // ===================== 조회 =====================

    // 내 채팅방 목록 (lastMessageAt 내림차순, unreadCount 포함) — 무한 스크롤
    // 배치 쿼리 3개로 N+1 제거: 참여자 수, 최신 메시지, Redis unread
    @Transactional(readOnly = true)
    public Slice<ChatRoomResponseDto> getMyRooms(Long accountId, Pageable pageable) {
        Slice<ChatRoom> rooms = chatRoomRepository.findMyRooms(accountId, pageable);
        if (rooms.isEmpty()) {
            return rooms.map(room -> ChatRoomResponseDto.of(room, null, 0L, 0L));
        }

        List<Long> roomIds = rooms.stream().map(ChatRoom::getChatroomId).toList();

        Map<Long, Long> participantCounts = chatParticipantRepository
                .countGroupedByRoomIdsAndStatus(roomIds, ParticipantStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        Map<Long, ChatMessage> latestMessages = chatMessageRepository
                .findLatestMessagesForRooms(roomIds)
                .stream()
                .collect(Collectors.toMap(
                        msg -> msg.getChatRoom().getChatroomId(),
                        msg -> msg,
                        (a, b) -> a.getSentAt().isAfter(b.getSentAt()) ? a : b
                ));

        return rooms.map(room -> {
            long participantCount = participantCounts.getOrDefault(room.getChatroomId(), 0L);
            long unread = resolveRoomUnread(room.getChatroomId(), accountId);
            String preview = Optional.ofNullable(latestMessages.get(room.getChatroomId()))
                    .map(ChatMessagePreview::of)
                    .orElse(null);
            return ChatRoomResponseDto.of(room, preview, unread, participantCount);
        });
    }

    // 채팅방 상세 (참여자 목록 포함)
    @Transactional(readOnly = true)
    public ChatRoomDetailResponseDto getRoomDetail(Long accountId, Long roomId) {
        ChatRoom room = chatAccessHelper.getRoomOrThrow(roomId);
        chatAccessHelper.verifyParticipant(accountId, roomId);

        List<ChatParticipantResponseDto> participants = chatParticipantRepository
                .findAllByChatRoom_ChatroomIdAndStatus(roomId, ParticipantStatus.ACTIVE)
                .stream()
                .map(ChatParticipantResponseDto::from)
                .toList();

        return ChatRoomDetailResponseDto.of(room, participants);
    }

    // ===================== 참여자 관리 =====================

    // 직접 입장 — 초대 없이 GROUP 채팅방에 스스로 참여
    // 락 획득 후 트랜잭션 시작 → 커밋 완료 후 락 해제 (중복 INSERT 방지)
    public void joinRoom(Long accountId, Long roomId) {
        withLockAndTx(LockKeys.chatRoomInvite(roomId), () -> {
            ChatRoom room = chatAccessHelper.getRoomOrThrow(roomId);
            chatAccessHelper.verifyRoomActive(room);
            chatAccessHelper.verifyGroupRoom(room);

            ChatParticipant existing = chatParticipantRepository
                    .findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, accountId)
                    .orElse(null);

            if (existing != null && existing.isActive()) {
                // DB 쓰기 없음 — AFTER_COMMIT 이벤트 대신 직접 리셋
                chatUnreadService.resetRoom(accountId, roomId);
                return;
            }

            Account account = getAccount(accountId);
            if (existing != null) {
                existing.rejoin();
            } else {
                chatParticipantRepository.save(ChatParticipant.create(room, account));
            }

            saveAndBroadcastSystemMessage(room, account,
                    String.format("%s님이 입장했습니다.", account.getName()));
            eventPublisher.publishEvent(new ChatRoomReadEvent(accountId, roomId));
            log.info("채팅방 직접 입장: roomId={}, accountId={}", roomId, accountId);
        });
    }

    // GROUP 채팅방 참여자 초대 + 입장 SYSTEM 메시지
    // 락 획득 후 트랜잭션 시작 → 커밋 완료 후 락 해제 (동시 초대 시 중복 참여자 방지)
    public void inviteParticipants(Long accountId, Long roomId, List<Long> accountIds) {
        withLockAndTx(LockKeys.chatRoomInvite(roomId), () -> {
            ChatRoom room = chatAccessHelper.getRoomOrThrow(roomId);
            chatAccessHelper.verifyRoomActive(room);
            chatAccessHelper.verifyParticipant(accountId, roomId);
            chatAccessHelper.verifyGroupRoom(room);

            // STORE 단톡방 초대는 가게 소유자만 가능
            if (room.getRefType() == ChatRoomRefType.STORE) {
                Store store = storeRepository.findById(room.getRefId())
                        .orElseThrow(() -> new NotFoundException(ErrorCode.STORE_NOT_FOUND));
                if (!store.isOwnedBy(accountId)) {
                    throw new ForbiddenException(ErrorCode.STORE_ACCESS_DENIED);
                }
            }

            List<Account> invitees = loadInvitees(accountIds, null);
            List<String> joinedNames = new ArrayList<>();

            for (Account invitee : invitees) {
                ChatParticipant existing = chatParticipantRepository
                        .findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, invitee.getAccountId())
                        .orElse(null);
                if (existing == null) {
                    chatParticipantRepository.save(ChatParticipant.create(room, invitee));
                    joinedNames.add(invitee.getName());
                } else if (!existing.isActive()) {
                    existing.rejoin();
                    joinedNames.add(invitee.getName());
                }
            }

            if (!joinedNames.isEmpty()) {
                saveAndBroadcastSystemMessage(room, getAccount(accountId),
                        String.format("%s님이 입장했습니다.", String.join(", ", joinedNames)));
            }
            log.info("채팅방 참여자 초대: roomId={}, 신규={}명", roomId, joinedNames.size());
        });
    }

    // 채팅방 나가기 (status=LEFT, leftAt 기록). GROUP 전체 퇴장 시 isActive=false
    // 락 획득 후 트랜잭션 시작 → 커밋 완료 후 락 해제 (동시 퇴장 시 비활성화 중복 처리 방지)
    public void leaveRoom(Long accountId, Long roomId) {
        withLockAndTx(LockKeys.chatRoomLeave(roomId), () -> {
            ChatRoom room = chatAccessHelper.getRoomOrThrow(roomId);
            ChatParticipant participant = chatAccessHelper.verifyParticipant(accountId, roomId);
            participant.leave();
            eventPublisher.publishEvent(new ChatRoomReadEvent(accountId, roomId));

            Account actor = participant.getAccount();
            saveAndBroadcastSystemMessage(room, actor,
                    String.format("%s님이 나갔습니다.", actor.getName()));

            long activeCount = chatParticipantRepository
                    .countByChatRoom_ChatroomIdAndStatus(roomId, ParticipantStatus.ACTIVE);
            if (activeCount == 0) {
                room.deactivate();
                log.info("채팅방 전체 퇴장 → 비활성화: roomId={}", roomId);
            }
        });
    }

    // ===================== 읽음 처리 =====================

    // lastReadTime 갱신 → AFTER_COMMIT 후 unread 캐시 리셋 (REST 엔드포인트용)
    @Transactional
    public void markRoomAsRead(Long accountId, Long roomId) {
        doMarkRoomAsRead(accountId, roomId);
    }

    // WebSocket 읽음 처리 — 단일 트랜잭션 안에서 lastReadTime 갱신 + 최신 messageId 조회
    // 두 트랜잭션으로 분리 시 그 사이 신규 메시지 유입으로 lastReadMessageId가 부정확해지는 문제 방지
    @Transactional
    public ChatReadResponseDto markRoomAsReadWithResult(Long accountId, Long roomId) {
        LocalDateTime readAt = doMarkRoomAsRead(accountId, roomId);
        Long lastReadMessageId = chatMessageRepository
                .findFirstByChatRoom_ChatroomIdOrderBySentAtDesc(roomId)
                .map(ChatMessage::getChatmessageId)
                .orElse(null);
        return ChatReadResponseDto.builder()
                .roomId(roomId)
                .accountId(accountId)
                .lastReadMessageId(lastReadMessageId)
                .readAt(readAt)
                .build();
    }

    // 참여자 lastReadTime 갱신 + unread 리셋 이벤트 발행 공통 로직
    private LocalDateTime doMarkRoomAsRead(Long accountId, Long roomId) {
        ChatParticipant participant = chatAccessHelper.verifyParticipant(accountId, roomId);
        LocalDateTime readAt = LocalDateTime.now();
        participant.updateLastReadTime(readAt);
        eventPublisher.publishEvent(new ChatRoomReadEvent(accountId, roomId));
        return readAt;
    }

    // WebSocket 타이핑 인디케이터용 — 참여자 검증 후 nickname 반환 (Account LAZY → 트랜잭션 내 처리)
    @Transactional(readOnly = true)
    public String verifyParticipantAndGetNickname(Long accountId, Long roomId) {
        ChatParticipant participant = chatAccessHelper.verifyParticipant(accountId, roomId);
        return participant.getAccount().getNickname();
    }

    // ===================== 내부 헬퍼 =====================

    // 락 획득 + 트랜잭션 실행 (반환값 있음)
    private <T> T withLockAndTx(String lockKey, Supplier<T> action) {
        return redisLockService.executeWithLock(lockKey, Duration.ofSeconds(5),
                () -> transactionTemplate.execute(status -> action.get()));
    }

    // 락 획득 + 트랜잭션 실행 (반환값 없음)
    private void withLockAndTx(String lockKey, Runnable action) {
        redisLockService.executeWithLock(lockKey, Duration.ofSeconds(5),
                () -> transactionTemplate.execute(status -> { action.run(); return null; }));
    }

    // SYSTEM 메시지 저장 + lastMessageAt 갱신 + WS 브로드캐스트 이벤트 발행
    private void saveAndBroadcastSystemMessage(ChatRoom room, Account actor, String content) {
        ChatMessage system = ChatMessage.system(room, actor, content);
        chatMessageRepository.save(system);
        room.updateLastMessageAt(system.getSentAt());
        eventPublisher.publishEvent(
                new ChatMessageBroadcastEvent(room.getChatroomId(), ChatMessageResponseDto.from(system)));
    }

    // STORE 단톡방 자동 이름 / 일반 그룹 이름 결정
    private String resolveRoomName(String rawName, boolean isStoreRoom, Store store) {
        if (rawName == null || rawName.isBlank()) {
            if (isStoreRoom) return store.getName() + " 단톡방";
            throw new BadRequestException(ErrorCode.CHAT_NAME_REQUIRED);
        }
        return rawName;
    }

    // 지역 내 공개 채팅방 목록 (GROUP/GROUP_STREET) — 입장 전 탐색용, 무한 스크롤
    @Transactional(readOnly = true)
    public Slice<ChatRoomPublicResponseDto> getPublicRooms(Long accountId, Pageable pageable) {
        Account account = getAccount(accountId);
        Region region = getRegionOrThrow(account);

        Slice<ChatRoom> rooms = chatRoomRepository.findPublicRooms(region.getRegionId(), pageable);
        if (rooms.isEmpty()) {
            return rooms.map(r -> ChatRoomPublicResponseDto.of(r, 0L, false));
        }

        List<Long> roomIds = rooms.stream().map(ChatRoom::getChatroomId).toList();

        Map<Long, Long> participantCounts = chatParticipantRepository
                .countGroupedByRoomIdsAndStatus(roomIds, ParticipantStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        Set<Long> joinedRoomIds = chatParticipantRepository
                .findAllByAccount_AccountIdAndStatus(accountId, ParticipantStatus.ACTIVE)
                .stream()
                .map(p -> p.getChatRoom().getChatroomId())
                .collect(Collectors.toSet());

        return rooms.map(room -> ChatRoomPublicResponseDto.of(
                room,
                participantCounts.getOrDefault(room.getChatroomId(), 0L),
                joinedRoomIds.contains(room.getChatroomId())
        ));
    }

    private Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    // 생성자의 인증된 주요 지역 반환 (없으면 null)
    private Region getCreatorRegionOrNull(Account creator) {
        Long primaryId = creator.getPrimaryRegionId();
        if (primaryId == null) return null;
        return accountRegionRepository
                .findByAccountRegionIdAndAccount_AccountId(primaryId, creator.getAccountId())
                .filter(AccountRegion::isVerified)
                .map(AccountRegion::getRegion)
                .orElse(null);
    }

    // 사용자의 인증된 주요 지역 반환 (없으면 예외)
    private Region getRegionOrThrow(Account account) {
        Long primaryId = account.getPrimaryRegionId();
        if (primaryId == null) {
            throw new NotFoundException(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND);
        }
        return accountRegionRepository
                .findByAccountRegionIdAndAccount_AccountId(primaryId, account.getAccountId())
                .filter(AccountRegion::isVerified)
                .map(AccountRegion::getRegion)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_PRIMARY_REGION_NOT_FOUND));
    }

    // 초대 대상 로드 (중복 제거, excludeAccountId 제외)
    private List<Account> loadInvitees(List<Long> accountIds, Long excludeAccountId) {
        if (accountIds == null || accountIds.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>(accountIds);
        if (excludeAccountId != null) {
            ids.remove(excludeAccountId);
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        return accountRepository.findAllById(ids);
    }

    private ChatRoomResponseDto toResponseDto(ChatRoom room, Long accountId) {
        long participantCount = chatParticipantRepository
                .countByChatRoom_ChatroomIdAndStatus(room.getChatroomId(), ParticipantStatus.ACTIVE);
        long unread = resolveRoomUnread(room.getChatroomId(), accountId);
        String preview = buildPreview(room.getChatroomId());
        return ChatRoomResponseDto.of(room, preview, unread, participantCount);
    }

    private long resolveRoomUnread(Long roomId, Long accountId) {
        return chatUnreadService.getRoomUnread(accountId, roomId, () -> {
            ChatParticipant participant = chatParticipantRepository
                    .findByChatRoom_ChatroomIdAndAccount_AccountId(roomId, accountId)
                    .orElse(null);
            if (participant == null) return 0L;
            return chatMessageRepository.countByChatRoom_ChatroomIdAndSentAtAfterAndAccount_AccountIdNot(
                    roomId, participant.unreadSince(), accountId);
        });
    }

    private String buildPreview(Long roomId) {
        return chatMessageRepository.findFirstByChatRoom_ChatroomIdOrderBySentAtDesc(roomId)
                .map(ChatMessagePreview::of)
                .orElse(null);
    }
}
