package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiGeneratedMessageUpdateRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiGeneratedMessageResponseDto;
import com.eeum.eeum.domain.ai.entity.AiActionLog;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.event.AiMessageSentEvent;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.store.entity.StoreNotice;
import com.eeum.eeum.domain.store.enums.StoreNoticeType;
import com.eeum.eeum.domain.store.repository.StoreNoticeRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 메시지 상태 전이 Executor — @Transactional이 Redis 락 안에서 시작되도록 별도 Bean으로 분리.
 * AiGeneratedMessageService가 락을 먼저 잡고, 이 Executor의 메서드를 호출해 TX를 시작/커밋한다.
 * TX 커밋 후 락 해제 순서를 보장해 중복 발송 레이스 윈도우를 제거한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiMessageCommandExecutor {

    private final AiManagerSupportService supportService;
    private final AiActionLogRepository aiActionLogRepository;
    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final StoreNoticeRepository storeNoticeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AiGeneratedMessageResponseDto updateInTx(Long ownerId, Long messageId, AiGeneratedMessageUpdateRequestDto request) {
        AiGeneratedMessage message = supportService.getOwnedMessage(ownerId, messageId);
        message.edit(request.getTitle(), request.getContent());
        return AiGeneratedMessageResponseDto.from(message);
    }

    @Transactional
    public AiGeneratedMessageResponseDto sendInTx(Long ownerId, Long messageId) {
        AiGeneratedMessage message = supportService.getOwnedMessage(ownerId, messageId);
        validateNonNoticeMessageType(message.getType());
        message.send(LocalDateTime.now());
        publishSideEffects(message, AiActionType.MESSAGE_SENT, "메시지 발송 처리", false);
        return AiGeneratedMessageResponseDto.from(message);
    }

    @Transactional
    public AiGeneratedMessageResponseDto scheduleInTx(Long ownerId, Long messageId, LocalDateTime scheduledAt) {
        AiGeneratedMessage message = supportService.getOwnedMessage(ownerId, messageId);
        validateNonNoticeMessageType(message.getType());
        message.schedule(scheduledAt, LocalDateTime.now());
        publishSideEffects(message, AiActionType.MESSAGE_SCHEDULED, "메시지 예약 발송 등록", true);
        return AiGeneratedMessageResponseDto.from(message);
    }

    @Transactional
    public AiGeneratedMessageResponseDto cancelInTx(Long ownerId, Long messageId) {
        AiGeneratedMessage message = supportService.getOwnedMessage(ownerId, messageId);
        message.cancel();
        aiActionLogRepository.save(AiActionLog.record(
                message.getStore(), message.getOwnerAccount(), AiActionType.MESSAGE_CANCELLED,
                message.getType().name(), messageId, "메시지 취소"));
        return AiGeneratedMessageResponseDto.from(message);
    }

    @Transactional
    public AiGeneratedMessageResponseDto publishNoticeInTx(Long ownerId, Long messageId) {
        AiGeneratedMessage message = supportService.getOwnedMessage(ownerId, messageId);
        validateNoticeChannel(message.getChannel());
        validateNoticeMessageType(message.getType());
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
    }

    @Transactional
    public AiGeneratedMessageResponseDto scheduleNoticeInTx(Long ownerId, Long messageId, LocalDateTime scheduledAt) {
        AiGeneratedMessage message = supportService.getOwnedMessage(ownerId, messageId);
        validateNoticeChannel(message.getChannel());
        validateNoticeMessageType(message.getType());
        message.schedule(scheduledAt, LocalDateTime.now());
        publishSideEffects(message, AiActionType.MESSAGE_SCHEDULED, "공지 예약 등록", true);
        return AiGeneratedMessageResponseDto.from(message);
    }

    // 디스패치 실패 시 FAILED 상태로 전이 — REQUIRES_NEW로 본 TX와 독립적으로 커밋
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedInTx(Long messageId) {
        aiGeneratedMessageRepository.findById(messageId)
                .ifPresent(AiGeneratedMessage::markFailed);
    }

    private void validateNoticeChannel(AiChannel channel) {
        if (channel == null || !channel.isNoticeSendable()) {
            throw new BusinessException(ErrorCode.AI_INVALID_CHANNEL);
        }
    }

    // 공지/이벤트 마케팅 타입만 허용 — 일반 메시지가 공지 경로를 통하지 못하도록 차단
    private void validateNoticeMessageType(AiMessageType type) {
        if (type != AiMessageType.NOTICE && type != AiMessageType.EVENT_MARKETING) {
            throw new BusinessException(ErrorCode.AI_INVALID_MESSAGE_TYPE);
        }
    }

    // 공지/이벤트 마케팅 타입은 publishNotice/scheduleNotice 경로를 사용해야 함
    private void validateNonNoticeMessageType(AiMessageType type) {
        if (type == AiMessageType.NOTICE || type == AiMessageType.EVENT_MARKETING) {
            throw new BusinessException(ErrorCode.AI_INVALID_MESSAGE_TYPE);
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
