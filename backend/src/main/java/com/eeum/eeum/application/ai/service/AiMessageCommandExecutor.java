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
import com.eeum.eeum.domain.inquiry.entity.Inquiry;
import com.eeum.eeum.domain.inquiry.entity.InquiryAnswer;
import com.eeum.eeum.domain.inquiry.enums.InquiryAnswerWriterType;
import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;
import com.eeum.eeum.domain.inquiry.event.InquiryAnsweredEvent;
import com.eeum.eeum.domain.inquiry.repository.InquiryAnswerRepository;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.store.entity.StoreNotice;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.entity.StoreReviewReply;
import com.eeum.eeum.domain.store.enums.StoreNoticeType;
import com.eeum.eeum.domain.store.event.StoreReviewReplyCreatedEvent;
import com.eeum.eeum.domain.store.repository.StoreNoticeRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewReplyRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
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
    private final StoreRepository storeRepository;
    private final InquiryRepository inquiryRepository;
    private final InquiryAnswerRepository inquiryAnswerRepository;
    private final StoreReviewRepository storeReviewRepository;
    private final StoreReviewReplyRepository storeReviewReplyRepository;
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
        applyLinkedDomainSideEffectInCurrentTransaction(message);
        publishSideEffects(message, AiActionType.MESSAGE_SENT, "메시지 발송 처리", false);
        return AiGeneratedMessageResponseDto.from(message);
    }

    // 메시지 타입에 따라 연결된 실제 도메인(문의/리뷰) 상태까지 반영 — AI 메시지 SENT 전이와 같은 트랜잭션에서 처리되어
    // 도메인 반영이 실패하면 message.send()도 함께 롤백된다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyLinkedDomainSideEffectInCurrentTransaction(AiGeneratedMessage message) {
        switch (message.getType()) {
            case INQUIRY_REPLY -> applyInquiryReply(message);
            case REVIEW_REPLY -> applyReviewReply(message);
            // CUSTOMER_CARE / COMPLAINT_REPLY — 1차 MVP에서는 연결된 발송 이력 테이블이 없어 SENT 처리 + 이벤트 발행만 수행
            case CUSTOMER_CARE, COMPLAINT_REPLY -> {
            }
            // NOTICE / EVENT_MARKETING은 validateNonNoticeMessageType에서 이미 차단되어 이 경로에 도달하지 않는다.
            case NOTICE, EVENT_MARKETING, LOCAL_MATCH, RISK_GUIDE, SAVING_PLAN -> {
            }
        }
    }

    // INQUIRY_REPLY 발송 → 문의 답변 등록 + PENDING → ANSWERED 전이 (OwnerInquiryService.answerInquiry와 동일한 정책)
    private void applyInquiryReply(AiGeneratedMessage message) {
        if (!"INQUIRY".equals(message.getTargetType()) || message.getTargetId() == null) {
            throw new BusinessException(ErrorCode.AI_INVALID_TARGET);
        }

        Inquiry inquiry = inquiryRepository.findByInquiryIdAndStore_StoreId(
                        message.getTargetId(), message.getStore().getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));

        if (inquiry.getTargetType() != InquiryTargetType.STORE) {
            throw new BusinessException(ErrorCode.INQUIRY_TARGET_TYPE_MISMATCH);
        }
        if (!inquiry.isAnswerable()) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }

        InquiryAnswer answer = InquiryAnswer.create(
                inquiry, message.getOwnerAccount(), InquiryAnswerWriterType.OWNER, message.getContent());
        inquiryAnswerRepository.save(answer);
        inquiry.markAnswered();

        log.info("[AI-MESSAGE] 문의 답변 반영 messageId={}, type={}, targetType={}, targetId={}, status={}",
                message.getAiGeneratedMessageId(), message.getType(), message.getTargetType(),
                message.getTargetId(), message.getStatus());

        eventPublisher.publishEvent(new InquiryAnsweredEvent(
                inquiry.getInquiryId(), inquiry.getWriter().getAccountId(), inquiry.getTitle()));
    }

    // REVIEW_REPLY 발송 → 리뷰 답글 등록 (StoreReviewService.createReply와 동일한 정책 — 리뷰당 답글 1개)
    private void applyReviewReply(AiGeneratedMessage message) {
        if (!"STORE_REVIEW".equals(message.getTargetType()) || message.getTargetId() == null) {
            throw new BusinessException(ErrorCode.AI_INVALID_TARGET);
        }

        Long storeId = message.getStore().getStoreId();
        storeRepository.findByIdWithPessimisticLock(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        StoreReview review = storeReviewRepository
                .findWithAccountAndStoreByStorereviewIdForUpdate(message.getTargetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_REVIEW_NOT_FOUND));
        if (!review.getStore().getStoreId().equals(storeId)) {
            throw new BusinessException(ErrorCode.STORE_REVIEW_NOT_FOUND);
        }

        if (storeReviewReplyRepository.existsByStoreReview_StorereviewId(review.getStorereviewId())) {
            throw new BusinessException(ErrorCode.STORE_REVIEW_REPLY_ALREADY_EXISTS);
        }

        StoreReviewReply reply = StoreReviewReply.create(review, message.getOwnerAccount(), message.getContent());
        // Store -> Review 순서의 비관적 잠금으로 답글 생성 경로를 직렬화했으므로 중복 여부는 위 검증으로
        // 확정된다. 그 밖의 FK/무결성 오류를 중복 답글로 오인하지 않도록 예외를 임의 변환하지 않는다.
        storeReviewReplyRepository.saveAndFlush(reply);

        log.info("[AI-MESSAGE] 리뷰 답글 반영 messageId={}, type={}, targetType={}, targetId={}, status={}",
                message.getAiGeneratedMessageId(), message.getType(), message.getTargetType(),
                message.getTargetId(), message.getStatus());

        eventPublisher.publishEvent(new StoreReviewReplyCreatedEvent(
                review.getAccount().getAccountId(), review.getStore().getName(),
                review.getStore().getStoreId(), review.getStorereviewId()));
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
