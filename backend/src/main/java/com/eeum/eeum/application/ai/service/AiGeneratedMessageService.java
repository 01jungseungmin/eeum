package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiGeneratedMessageUpdateRequestDto;
import com.eeum.eeum.application.ai.dto.request.AiMessageScheduleRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiGeneratedMessageResponseDto;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.common.lock.LockKeys;
import com.eeum.eeum.common.service.RedisLockService;
import com.eeum.eeum.domain.ai.entity.AiActionLog;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.event.AiMessageSentEvent;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreNotice;
import com.eeum.eeum.domain.store.enums.StoreNoticeType;
import com.eeum.eeum.domain.store.repository.StoreNoticeRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AiGeneratedMessageService {

    private static final Duration MESSAGE_LOCK_LEASE = Duration.ofSeconds(5);

    private final AiManagerSupportService supportService;
    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final AiActionLogRepository aiActionLogRepository;
    private final StoreNoticeRepository storeNoticeRepository;
    private final RedisLockService redisLockService;
    private final ApplicationEventPublisher eventPublisher;

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
        return AiGeneratedMessageResponseDto.from(supportService.getOwnedMessage(ownerId, messageId));
    }

    @Transactional
    public AiGeneratedMessageResponseDto updateMessage(Long ownerId, Long messageId, AiGeneratedMessageUpdateRequestDto request) {
        AiGeneratedMessage message = supportService.getOwnedMessage(ownerId, messageId);
        message.edit(request.getTitle(), request.getContent());
        return AiGeneratedMessageResponseDto.from(message);
    }

    // 검토 후 보내기 — 1차에서는 실제 외부 발송 없이 상태만 SENT로 변경하고 이벤트 발행
    @Transactional
    public AiGeneratedMessageResponseDto sendMessage(Long ownerId, Long messageId) {
        return redisLockService.executeWithLock(LockKeys.aiMessage(messageId), MESSAGE_LOCK_LEASE, () -> {
            AiGeneratedMessage message = supportService.getOwnedMessage(ownerId, messageId);
            message.send(LocalDateTime.now());
            publishSideEffects(message, AiActionType.MESSAGE_SENT, "메시지 발송 처리", false);
            return AiGeneratedMessageResponseDto.from(message);
        });
    }

    @Transactional
    public AiGeneratedMessageResponseDto scheduleMessage(Long ownerId, Long messageId, AiMessageScheduleRequestDto request) {
        return redisLockService.executeWithLock(LockKeys.aiMessage(messageId), MESSAGE_LOCK_LEASE, () -> {
            AiGeneratedMessage message = supportService.getOwnedMessage(ownerId, messageId);
            message.schedule(request.getScheduledAt(), LocalDateTime.now());
            publishSideEffects(message, AiActionType.MESSAGE_SCHEDULED, "메시지 예약 발송 등록", true);
            return AiGeneratedMessageResponseDto.from(message);
        });
    }

    @Transactional
    public AiGeneratedMessageResponseDto cancelMessage(Long ownerId, Long messageId) {
        return redisLockService.executeWithLock(LockKeys.aiMessage(messageId), MESSAGE_LOCK_LEASE, () -> {
            AiGeneratedMessage message = supportService.getOwnedMessage(ownerId, messageId);
            message.cancel();
            aiActionLogRepository.save(AiActionLog.record(
                    message.getStore(), message.getOwnerAccount(), AiActionType.MESSAGE_CANCELLED,
                    message.getType().name(), messageId, "메시지 취소"));
            return AiGeneratedMessageResponseDto.from(message);
        });
    }

    // 공지 등록 — SNS_CARD 채널은 발송 불가, STORE_NOTICE 채널이면 실제 StoreNotice 생성까지 연동
    @Transactional
    public AiGeneratedMessageResponseDto publishNotice(Long ownerId, Long messageId) {
        return redisLockService.executeWithLock(LockKeys.aiMessage(messageId), MESSAGE_LOCK_LEASE, () -> {
            AiGeneratedMessage message = supportService.getOwnedMessage(ownerId, messageId);
            validateNoticeChannel(message.getChannel());
            message.send(LocalDateTime.now());
            if (message.getChannel() == AiChannel.STORE_NOTICE) {
                storeNoticeRepository.save(StoreNotice.create(
                        message.getStore(),
                        message.getTitle() != null ? message.getTitle() : "가게 소식",
                        message.getContent(),
                        StoreNoticeType.NORMAL,
                        false));
            }
            publishSideEffects(message, AiActionType.NOTICE_PUBLISHED, "공지 등록", false);
            return AiGeneratedMessageResponseDto.from(message);
        });
    }

    @Transactional
    public AiGeneratedMessageResponseDto scheduleNotice(Long ownerId, Long messageId, AiMessageScheduleRequestDto request) {
        return redisLockService.executeWithLock(LockKeys.aiMessage(messageId), MESSAGE_LOCK_LEASE, () -> {
            AiGeneratedMessage message = supportService.getOwnedMessage(ownerId, messageId);
            validateNoticeChannel(message.getChannel());
            message.schedule(request.getScheduledAt(), LocalDateTime.now());
            publishSideEffects(message, AiActionType.MESSAGE_SCHEDULED, "공지 예약 등록", true);
            return AiGeneratedMessageResponseDto.from(message);
        });
    }

    private void validateNoticeChannel(AiChannel channel) {
        if (channel == null || !channel.isNoticeSendable()) {
            throw new BusinessException(ErrorCode.AI_INVALID_CHANNEL);
        }
    }

    private void publishSideEffects(AiGeneratedMessage message, AiActionType actionType, String description, boolean scheduled) {
        aiActionLogRepository.save(AiActionLog.record(
                message.getStore(), message.getOwnerAccount(), actionType,
                message.getType().name(), message.getAiGeneratedMessageId(), description));
        eventPublisher.publishEvent(new AiMessageSentEvent(
                message.getAiGeneratedMessageId(),
                message.getStore().getStoreId(),
                message.getType(),
                message.getChannel(),
                scheduled,
                LocalDateTime.now()));
    }
}
