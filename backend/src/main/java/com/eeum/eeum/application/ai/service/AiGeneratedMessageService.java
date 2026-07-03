package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiGeneratedMessageUpdateRequestDto;
import com.eeum.eeum.application.ai.dto.request.AiMessageScheduleRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiGeneratedMessageResponseDto;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.store.entity.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AiGeneratedMessageService {

    private static final Duration MESSAGE_LOCK_LEASE = Duration.ofSeconds(5);

    private final AiManagerSupportService supportService;
    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final RedisLockService redisLockService;
    private final AiMessageCommandExecutor messageCommandExecutor;

    @Transactional(readOnly = true)
    public Page<AiGeneratedMessageResponseDto> getMessages(Long ownerId, AiMessageType type, Pageable pageable) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.GENERATED_MESSAGE_MANAGE);
        Page<AiGeneratedMessage> messages = type == null
                ? aiGeneratedMessageRepository.findByStore_StoreIdOrderByCreatedAtDesc(store.getStoreId(), pageable)
                : aiGeneratedMessageRepository.findByStore_StoreIdAndTypeOrderByCreatedAtDesc(store.getStoreId(), type, pageable);
        return messages.map(AiGeneratedMessageResponseDto::from);
    }

    @Transactional(readOnly = true)
    public AiGeneratedMessageResponseDto getMessage(Long ownerId, Long messageId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.GENERATED_MESSAGE_MANAGE);
        return AiGeneratedMessageResponseDto.from(supportService.getOwnedMessage(ownerId, messageId));
    }

    // edit()도 상태 전이(SCHEDULED→REVIEWED)를 일으키므로 send/cancel과 동일한 락으로 직렬화
    public AiGeneratedMessageResponseDto updateMessage(Long ownerId, Long messageId, AiGeneratedMessageUpdateRequestDto request) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.GENERATED_MESSAGE_MANAGE);
        return redisLockService.executeWithLock(LockKeys.aiMessage(messageId), MESSAGE_LOCK_LEASE,
                () -> messageCommandExecutor.updateInTx(ownerId, messageId, request));
    }

    // 검토 후 보내기 — 락 먼저 잡고 → Executor에서 @Transactional 시작 → TX 커밋 후 락 해제
    public AiGeneratedMessageResponseDto sendMessage(Long ownerId, Long messageId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.GENERATED_MESSAGE_MANAGE);
        return redisLockService.executeWithLock(LockKeys.aiMessage(messageId), MESSAGE_LOCK_LEASE,
                () -> messageCommandExecutor.sendInTx(ownerId, messageId));
    }

    public AiGeneratedMessageResponseDto scheduleMessage(Long ownerId, Long messageId, AiMessageScheduleRequestDto request) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.GENERATED_MESSAGE_MANAGE);
        return redisLockService.executeWithLock(LockKeys.aiMessage(messageId), MESSAGE_LOCK_LEASE,
                () -> messageCommandExecutor.scheduleInTx(ownerId, messageId, request.getScheduledAt()));
    }

    public AiGeneratedMessageResponseDto cancelMessage(Long ownerId, Long messageId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.GENERATED_MESSAGE_MANAGE);
        return redisLockService.executeWithLock(LockKeys.aiMessage(messageId), MESSAGE_LOCK_LEASE,
                () -> messageCommandExecutor.cancelInTx(ownerId, messageId));
    }

    // 공지 등록 — SNS_CARD 채널은 발송 불가, STORE_NOTICE 채널이면 실제 StoreNotice 생성까지 연동
    public AiGeneratedMessageResponseDto publishNotice(Long ownerId, Long messageId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.GENERATED_MESSAGE_MANAGE);
        return redisLockService.executeWithLock(LockKeys.aiMessage(messageId), MESSAGE_LOCK_LEASE,
                () -> messageCommandExecutor.publishNoticeInTx(ownerId, messageId));
    }

    public AiGeneratedMessageResponseDto scheduleNotice(Long ownerId, Long messageId, AiMessageScheduleRequestDto request) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.GENERATED_MESSAGE_MANAGE);
        return redisLockService.executeWithLock(LockKeys.aiMessage(messageId), MESSAGE_LOCK_LEASE,
                () -> messageCommandExecutor.scheduleNoticeInTx(ownerId, messageId, request.getScheduledAt()));
    }
}
