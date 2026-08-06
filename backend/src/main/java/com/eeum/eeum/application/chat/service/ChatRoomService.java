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
import com.eeum.eeum.domain.chat.event.ChatRoomClosedEvent;
import com.eeum.eeum.domain.chat.event.ChatRoomReadEvent;
import com.eeum.eeum.domain.chat.event.ChatRoomUnreadBulkResetEvent;
import com.eeum.eeum.exception.BadRequestException;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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
        validateReference(refType, request.getRefId());
        boolean isStoreRoom = refType == ChatRoomRefType.STORE && request.getRefId() != null;

        Supplier<ChatRoomResponseDto> create = () -> {
            Store store = null;
            if (isStoreRoom) {
                // 가게행을 먼저 잠가 종료/재생성과 DB 레벨에서 직렬화한다.
                store = storeRepository.findByIdWithPessimisticLock(request.getRefId())
                        .orElseThrow(() -> new NotFoundException(ErrorCode.STORE_NOT_FOUND));
                if (!store.isOwnedBy(accountId)) {
                    throw new ForbiddenException(ErrorCode.STORE_ACCESS_DENIED);
                }
                // 종료된 방은 재사용 대상이 아니다 — ACTIVE 방이 있을 때만 멱등 반환하고,
                // 없으면(전부 종료됐으면) 새 roomId로 새 방을 만든다.
                Optional<ChatRoom> existing = findActiveStoreRoom(request.getRefId());
                if (existing.isPresent()) {
                    log.info("가게 활성 단톡방 이미 존재 → 기존 방 반환: roomId={}, storeId={}",
                            existing.get().getChatroomId(), request.getRefId());
                    return toResponseDto(existing.get(), accountId);
                }
            }

            Account creator = getAccount(accountId);
            Region creatorRegion = getCreatorRegionOrNull(creator);
            ChatRoom room = ChatRoom.createGroup(creator, type,
                    resolveRoomName(request.getName(), isStoreRoom, store), refType, request.getRefId(),
                    creatorRegion);
            saveRoomOrThrowOnDuplicate(room);
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
        };

        // STORE 단톡방만 락으로 보호한다 — "가게당 ACTIVE 방 1개"라는 멱등 규칙이 있어
        // 조회~생성 구간을 직렬화할 실익이 있고, 종료/전원퇴장과도 같은 키로 맞물린다.
        //
        // 비STORE GROUP 방은 중복 판정 자체가 없어(같은 이름의 방을 여러 개 만드는 것이 허용) 락이
        // 중복을 막지 못한다. 그러면서 RedisLockService는 대기 없이 즉시 실패하므로, 생성자 단위 락은
        // 정상적인 동시 요청만 LOCK_ACQUIRE_FAILED로 떨어뜨리는 부작용만 남는다 — 그래서 걸지 않는다.
        // 추후 중복 금지 정책이 정해지면 참여자 조합 기준 제약과 함께 락을 다시 설계해야 한다.
        return isStoreRoom
                ? withLockAndTx(LockKeys.chatRoomStore(request.getRefId()), create)
                : withTx(create);
    }

    // ===================== 조회 =====================

    // 내 채팅방 목록 (lastMessageAt 내림차순, unreadCount 포함) — 무한 스크롤
    // 배치 쿼리 3개로 N+1 제거: 참여자 수, 최신 메시지, Redis unread
    // includeClosed=true면 종료된 방까지 반환한다 — 종료 시 참여자를 LEFT로 바꾸지 않으므로
    // 대화 기록은 DB에 남아 있고, 이 플래그가 유일한 열람 경로다 (응답의 active로 구분).
    @Transactional(readOnly = true)
    public Slice<ChatRoomResponseDto> getMyRooms(Long accountId, Pageable pageable, boolean includeClosed) {
        Slice<ChatRoom> rooms = chatRoomRepository.findMyRooms(accountId, pageable, includeClosed);
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
            long unread = resolveRoomUnread(room, accountId);
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

    // 직접 입장 — 초대 없이 일반 GROUP 및 공개된 STORE 채팅방에 스스로 참여
    // 락 획득 후 트랜잭션 시작 → 커밋 완료 후 락 해제 (중복 INSERT 방지)
    // 종료와 같은 방 단위 락을 쓴다 — 별도 키를 쓰면 종료 직전 활성 검증을 통과한 입장이
    // 종료 커밋 이후에 참여자를 남길 수 있다.
    public void joinRoom(Long accountId, Long roomId) {
        ChatRoom target = chatAccessHelper.getRoomOrThrow(roomId);
        withLockAndTx(roomStateLockKey(target), () -> {
            // Redis lease가 만료되어도 종료와 같은 방 행에서 DB 커밋까지 직렬화한다.
            ChatRoom room = lockRoomState(target);
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
            verifyDirectJoinRegion(account, room);
            if (existing != null) {
                existing.rejoin();
            } else {
                saveParticipantOrThrowOnDuplicate(room, account);
            }

            saveAndBroadcastSystemMessage(room, account,
                    String.format("%s님이 입장했습니다.", account.getName()));
            eventPublisher.publishEvent(new ChatRoomReadEvent(accountId, roomId));
            log.info("채팅방 직접 입장: roomId={}, accountId={}", roomId, accountId);
        });
    }

    // GROUP 채팅방 참여자 초대 + 입장 SYSTEM 메시지
    // 락 획득 후 트랜잭션 시작 → 커밋 완료 후 락 해제 (동시 초대 시 중복 참여자 방지)
    // 종료와 같은 방 단위 락을 쓴다 (joinRoom 주석 참고)
    public void inviteParticipants(Long accountId, Long roomId, List<Long> accountIds) {
        ChatRoom target = chatAccessHelper.getRoomOrThrow(roomId);
        withLockAndTx(roomStateLockKey(target), () -> {
            ChatRoom room = lockRoomState(target);
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
                    saveParticipantOrThrowOnDuplicate(room, invitee);
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
        // 마지막 참여자 퇴장은 방을 종료시키므로 종료와 동일한 락 키를 써야 한다.
        // STORE 방에서 leave가 chatRoomLeave를, 생성이 chatRoomStore를 잡으면 서로 직렬화되지 않아
        // 생성 쪽이 "곧 종료될 방"을 기존 ACTIVE 방으로 오인해 죽은 roomId를 반환한다.
        ChatRoom target = chatAccessHelper.getRoomOrThrow(roomId);
        withLockAndTx(roomStateLockKey(target), () -> {
            ChatRoom room = lockRoomState(target);
            ChatParticipant participant = chatAccessHelper.verifyParticipant(accountId, roomId);
            participant.leave();
            eventPublisher.publishEvent(new ChatRoomReadEvent(accountId, roomId));

            Account actor = participant.getAccount();
            saveAndBroadcastSystemMessage(room, actor,
                    String.format("%s님이 나갔습니다.", actor.getName()));

            long activeCount = chatParticipantRepository
                    .countByChatRoom_ChatroomIdAndStatus(roomId, ParticipantStatus.ACTIVE);
            if (activeCount == 0) {
                // 종료 경로가 어디든 동일한 뒷정리(unread 회수 + 종료 통지)를 거치도록 공통 처리에 위임.
                // 퇴장 SYSTEM 메시지 직후이므로 종료 메시지는 중복 발행하지 않는다.
                closeRoomInternal(roomId, null, null, null);
                log.info("채팅방 전체 퇴장 → 비활성화: roomId={}", roomId);
            }
        });
    }

    // ===================== 채팅방 종료(폭파) =====================

    // 채팅방 종료 — 물리 삭제가 아닌 isActive=false 전이(soft close)
    // 메시지/참여자 기록은 보존하고, 이후 메시지 발송·WebSocket 구독·목록 노출만 차단한다.
    // STORE 단톡방이면 가게 소유자, 그 외 GROUP 방이면 생성자만 종료할 수 있다.
    // 생성과 동일한 락 키를 사용해 "종료 ↔ 재생성" 사이의 레이스를 막는다.
    public void closeRoom(Long accountId, Long roomId) {
        ChatRoom target = chatAccessHelper.getRoomOrThrow(roomId);

        withLockAndTx(roomStateLockKey(target), () -> {
            ChatRoom room = lockRoomState(target);
            chatAccessHelper.verifyGroupRoom(room);
            verifyCloseAuthority(accountId, room);

            closeRoomInternal(roomId, () -> getAccount(accountId), accountId, "채팅방이 종료되었습니다.");
        });
    }

    // 관리자 강제 종료 — 권한/타입 검증만 생략하고 락·정리 절차는 사장 종료와 동일하게 재사용한다.
    // 락 없이 비활성화하면 같은 순간의 createGroupRoom이 곧 닫힐 방을 "기존 ACTIVE 방"으로 반환할 수 있다.
    public void forceCloseRoom(Long roomId) {
        ChatRoom target = chatAccessHelper.getRoomOrThrow(roomId);

        withLockAndTx(roomStateLockKey(target), () -> {
            lockRoomState(target);
            // 관리자는 actor 계정이 없으므로 방 생성자를 SYSTEM 메시지 발신자로 사용
            closeRoomInternal(roomId, () -> chatAccessHelper.getRoomOrThrow(roomId).getCreator(),
                    null, "관리자에 의해 채팅방이 종료되었습니다.");
            log.info("[ADMIN] 채팅방 강제 종료: roomId={}", roomId);
        });
    }

    // 종료 공통 처리 — 상태 전이 + unread 회수 + 종료 통지. 락은 호출자가 잡는다.
    // 조건부 UPDATE로 전이하고 영향 행 수가 1일 때만 후속 처리를 수행한다 (멱등).
    // actor는 Supplier로 받는다 — 멱등 반환 시 불필요한 계정 조회를 하지 않기 위함.
    private void closeRoomInternal(Long roomId, Supplier<Account> actorSupplier,
                                   Long closedByAccountId, String systemMessage) {
        LocalDateTime closedAt = LocalDateTime.now();
        if (chatRoomRepository.closeIfActive(roomId, closedAt) == 0) {
            log.info("이미 종료된 채팅방 — 종료 요청 무시: roomId={}", roomId);
            return;
        }

        // 위 벌크 UPDATE가 영속성 컨텍스트를 비우므로 이후 사용할 방은 다시 읽는다
        ChatRoom room = chatAccessHelper.getRoomOrThrow(roomId);
        if (actorSupplier != null && systemMessage != null) {
            saveAndBroadcastSystemMessage(room, actorSupplier.get(), systemMessage);
        }

        // 참여자는 LEFT로 바꾸지 않는다 — 종료 후에도 기존 참여자가 대화 기록을 열람할 수 있어야 한다.
        // 다만 Redis에 남은 방별 unread는 영원히 회수되지 않으므로 전원 일괄 리셋한다.
        List<Long> participantIds = chatParticipantRepository.findActiveAccountIds(roomId);
        if (!participantIds.isEmpty()) {
            eventPublisher.publishEvent(new ChatRoomUnreadBulkResetEvent(roomId, participantIds));
        }

        eventPublisher.publishEvent(
                new ChatRoomClosedEvent(roomId, closedByAccountId, room.getClosedAt()));
        log.info("채팅방 종료: roomId={}, closedBy={}, 참여자={}명",
                roomId, closedByAccountId, participantIds.size());
    }

    // 방 상태 변경 락 키 — 생성/입장/초대/퇴장/종료가 모두 이 키 하나로 직렬화된다.
    // 가게 단톡방은 생성(chatRoomStore)과 같은 키여야 "종료 ↔ 재생성" 레이스를 막을 수 있다.
    private String roomStateLockKey(ChatRoom room) {
        return room.isStoreRoom()
                ? LockKeys.chatRoomStore(room.getRefId())
                : LockKeys.chatRoomLeave(room.getChatroomId());
    }

    // 종료 권한 검증 — STORE 단톡방은 가게 소유자, 그 외 GROUP 방은 생성자
    private void verifyCloseAuthority(Long accountId, ChatRoom room) {
        if (room.isStoreRoom()) {
            Store store = storeRepository.findById(room.getRefId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.STORE_NOT_FOUND));
            if (!store.isOwnedBy(accountId)) {
                throw new ForbiddenException(ErrorCode.STORE_ACCESS_DENIED);
            }
            return;
        }
        if (!room.isCreatedBy(accountId)) {
            throw new ForbiddenException(ErrorCode.CHAT_ROOM_CLOSE_DENIED);
        }
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
        // 엔티티 dirty check 대신 조건부 UPDATE — 동시 읽음 처리 시 과거 시각이 최신 시각을 덮지 않도록
        chatParticipantRepository.advanceLastReadTime(participant.getChatparticipantId(), readAt);
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

    // STORE 방은 가게 행을 잠금·소유권·중복 판정의 기준으로 사용하므로 유효한 refId가 필수다.
    // USED_PRODUCT 등 다른 참조 타입은 해당 채팅방 기능이 구현될 때 각 도메인 정책으로 검증한다.
    private void validateReference(ChatRoomRefType refType, Long refId) {
        if (refType == ChatRoomRefType.STORE && (refId == null || refId <= 0)) {
            throw new BadRequestException(ErrorCode.CHAT_INVALID_REF_ID);
        }
    }

    // 락 획득 + 트랜잭션 실행 (반환값 있음)
    private <T> T withLockAndTx(String lockKey, Supplier<T> action) {
        return redisLockService.executeWithLock(lockKey, Duration.ofSeconds(5),
                () -> transactionTemplate.execute(status -> action.get()));
    }

    // 락 없이 트랜잭션만 실행 — 중복 판정이 없어 직렬화할 대상이 없는 경로용
    private <T> T withTx(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    // 락 획득 + 트랜잭션 실행 (반환값 없음)
    private void withLockAndTx(String lockKey, Runnable action) {
        redisLockService.executeWithLock(lockKey, Duration.ofSeconds(5),
                () -> transactionTemplate.execute(status -> { action.run(); return null; }));
    }

    // 활성 가게 단톡방 조회 — 종료된 방은 재사용 대상에서 제외하고, 여러 건이면 최신 방을 취한다.
    // 가게당 ACTIVE 단톡방은 type과 무관하게 1개다 (uk_chat_room_active_ref와 동일 기준).
    private Optional<ChatRoom> findActiveStoreRoom(Long storeId) {
        return chatRoomRepository
                .findFirstByRefTypeAndRefIdAndIsActiveTrueOrderByChatroomIdDesc(
                        ChatRoomRefType.STORE, storeId);
    }

    // 잠금 순서는 항상 Store → ChatRoom으로 고정한다.
    // 가게행은 종료/재생성 사이의 안정적인 mutex이고, 방행은 메시지 쓰기와 상태 변경을 직렬화한다.
    private ChatRoom lockRoomState(ChatRoom target) {
        if (target.isStoreRoom()) {
            storeRepository.findByIdWithPessimisticLock(target.getRefId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.STORE_NOT_FOUND));
        }
        return chatAccessHelper.getRoomWithPessimisticLockOrThrow(target.getChatroomId());
    }

    // 채팅방 저장 — Redis 락이 유실된 상황에서도 ACTIVE 단톡방 중복이 생기지 않도록
    // DB 유니크(uk_chat_room_active_ref) 위반을 409로 변환한다 (조용한 중복 생성보다 명시적 실패가 안전)
    private void saveRoomOrThrowOnDuplicate(ChatRoom room) {
        try {
            chatRoomRepository.saveAndFlush(room);
        } catch (DataIntegrityViolationException e) {
            log.warn("활성 채팅방 중복 생성 차단: refType={}, refId={}, type={}",
                    room.getRefType(), room.getRefId(), room.getType());
            throw new ConflictException(ErrorCode.CHAT_ROOM_ALREADY_EXISTS);
        }
    }

    // 참여자 저장 — "조회 후 없으면 INSERT"는 Redis 락(5초 lease)이 만료되면 중복 행을 만든다.
    // 중복이 생기면 이후 그 방의 모든 참여자 검증이 IncorrectResultSizeDataAccessException으로 500이 되므로,
    // DB 유니크(uk_chat_participant_room_account) 위반을 409로 변환해 중복 자체를 차단한다.
    // 409를 받은 클라이언트가 재시도하면 이번엔 "이미 참여 중" 분기를 타 정상 처리된다.
    private void saveParticipantOrThrowOnDuplicate(ChatRoom room, Account account) {
        try {
            chatParticipantRepository.saveAndFlush(ChatParticipant.create(room, account));
        } catch (DataIntegrityViolationException e) {
            log.warn("채팅방 참여자 중복 INSERT 차단: roomId={}, accountId={}",
                    room.getChatroomId(), account.getAccountId());
            throw new ConflictException(ErrorCode.CHAT_PARTICIPANT_DUPLICATE);
        }
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

    // 공개 목록과 직접 입장의 지역 경계를 동일하게 유지한다.
    // 이미 ACTIVE 참여자인 사용자는 초대 등으로 권한을 얻은 상태이므로 joinRoom의 조기 반환에서 제외하고,
    // 신규 참여와 LEFT 재입장에만 현재 인증된 대표 지역을 검증한다.
    private void verifyDirectJoinRegion(Account account, ChatRoom room) {
        Long primaryAccountRegionId = account.getPrimaryRegionId();
        Region roomRegion = room.getRegion();
        if (primaryAccountRegionId == null || roomRegion == null) {
            throw new ForbiddenException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }

        boolean sameVerifiedRegion = accountRegionRepository
                .findByAccountRegionIdAndAccount_AccountId(
                        primaryAccountRegionId, account.getAccountId())
                .filter(AccountRegion::isVerified)
                .map(AccountRegion::getRegionId)
                .filter(roomRegion.getRegionId()::equals)
                .isPresent();

        if (!sameVerifiedRegion) {
            throw new ForbiddenException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }
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
        long unread = resolveRoomUnread(room, accountId);
        String preview = buildPreview(room.getChatroomId());
        return ChatRoomResponseDto.of(room, preview, unread, participantCount);
    }

    // 종료된 방은 항상 0 — 읽어서 회수할 수단이 없으므로 배지를 남기지 않는다
    private long resolveRoomUnread(ChatRoom room, Long accountId) {
        if (!room.isActive()) {
            return 0L;
        }
        Long roomId = room.getChatroomId();
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
