package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiActivitySummaryResponseDto;
import com.eeum.eeum.application.ai.generator.AiInsightGenerator;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiCareType;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.store.entity.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiActivityService {

    private final AiManagerSupportService supportService;
    private final AiInsightGenerator aiInsightGenerator;
    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final InquiryRepository inquiryRepository;

    @Transactional(readOnly = true)
    public AiActivitySummaryResponseDto getActivitySummary(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.ACTIVITY_SUMMARY);
        Long storeId = store.getStoreId();

        LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);
        List<AiGeneratedMessage> recentMessages =
                aiGeneratedMessageRepository.findByStore_StoreIdAndCreatedAtAfter(storeId, monthAgo);

        long reviewReplyDrafts = countByType(recentMessages, AiMessageType.REVIEW_REPLY);
        long inquiryReplyDrafts = countByType(recentMessages, AiMessageType.INQUIRY_REPLY);
        long regularMessages = recentMessages.stream()
                .filter(message -> message.getType() == AiMessageType.CUSTOMER_CARE)
                .filter(message -> !message.hasCareType(AiCareType.INACTIVE_REGULAR))
                .count();
        long inactiveAlerts = recentMessages.stream()
                .filter(message -> message.getType() == AiMessageType.CUSTOMER_CARE)
                .filter(message -> message.hasCareType(AiCareType.INACTIVE_REGULAR))
                .count();
        long draftCount = recentMessages.stream()
                .filter(message -> message.getStatus() == AiMessageStatus.DRAFT)
                .count();
        long sentCount = recentMessages.stream()
                .filter(message -> message.getStatus() == AiMessageStatus.SENT)
                .count();

        return AiActivitySummaryResponseDto.builder()
                .period("최근 30일")
                .reviewReplyDraftCount(reviewReplyDrafts)
                .inquiryReplyDraftCount(inquiryReplyDrafts)
                .regularMessageCount(regularMessages)
                .inactiveAlertCount(inactiveAlerts)
                .draftCount(draftCount)
                .sentCount(sentCount)
                .revisitAfterMessageCount(null) // 재방문 추적 미연동 — 2차에서 제공
                .orderConversionAfterEventCount(null) // 알림-주문 연결 추적 미연동 — 2차에서 제공
                .unansweredInquiryRemainingCount(
                        inquiryRepository.countByStore_StoreIdAndStatus(storeId, InquiryStatus.PENDING))
                .highlight(aiInsightGenerator.activityHighlight(store.getName(), draftCount, sentCount))
                .build();
    }

    private long countByType(List<AiGeneratedMessage> messages, AiMessageType type) {
        return messages.stream().filter(message -> message.getType() == type).count();
    }
}
