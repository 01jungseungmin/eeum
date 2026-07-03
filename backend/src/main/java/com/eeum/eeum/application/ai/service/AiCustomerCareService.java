package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.request.AiCustomerCareDraftRequestDto;
import com.eeum.eeum.application.ai.dto.response.AiCustomerCareCardDto;
import com.eeum.eeum.application.ai.dto.response.AiGeneratedMessageResponseDto;
import com.eeum.eeum.application.ai.generator.AiText;
import com.eeum.eeum.application.ai.generator.AiTextGenerator;
import com.eeum.eeum.application.ai.policy.AiFeature;
import com.eeum.eeum.domain.ai.entity.AiActionLog;
import com.eeum.eeum.domain.ai.entity.AiGeneratedMessage;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import com.eeum.eeum.domain.ai.enums.AiCareType;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
import com.eeum.eeum.domain.ai.enums.AiUsageType;
import com.eeum.eeum.domain.ai.repository.AiActionLogRepository;
import com.eeum.eeum.domain.ai.repository.AiGeneratedMessageRepository;
import com.eeum.eeum.domain.inquiry.enums.InquiryStatus;
import com.eeum.eeum.domain.inquiry.repository.InquiryRepository;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.CartRepository;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiCustomerCareService {

    private final AiManagerSupportService supportService;
    private final AiTextGenerator aiTextGenerator;
    private final AiGeneratedMessageRepository aiGeneratedMessageRepository;
    private final AiActionLogRepository aiActionLogRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final InquiryRepository inquiryRepository;

    @Transactional(readOnly = true)
    public List<AiCustomerCareCardDto> getCareCards(Long ownerId) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.CUSTOMER_CARE_VIEW);
        return List.of(
                buildCard(store, AiCareType.CART_INTEREST),
                buildCard(store, AiCareType.INACTIVE_REGULAR),
                buildCard(store, AiCareType.INQUIRY_HESITATION)
        );
    }

    @Transactional(readOnly = true)
    public AiCustomerCareCardDto getCareCard(Long ownerId, AiCareType careType) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.validateFeature(store, AiFeature.CUSTOMER_CARE_VIEW);
        return buildCard(store, careType);
    }

    @Transactional
    public AiGeneratedMessageResponseDto createDraft(Long ownerId, AiCareType careType, AiCustomerCareDraftRequestDto request) {
        Store store = supportService.getOwnerStore(ownerId);
        supportService.consumeGeneration(store, ownerId, AiFeature.CUSTOMER_CARE_DRAFT, AiUsageType.CUSTOMER_CARE_DRAFT);

        String contextHint = request != null ? request.getContextHint() : null;
        AiChannel channel = request != null && request.getChannel() != null ? request.getChannel() : AiChannel.APP_PUSH;

        AiText text = aiTextGenerator.customerCareMessage(careType, store.getName(), contextHint);
        AiGeneratedMessage message = aiGeneratedMessageRepository.save(
                AiGeneratedMessage.createDraft(
                        store, store.getAccount(), AiMessageType.CUSTOMER_CARE,
                        careType.name(), null, text.title(), text.content(), channel));

        aiActionLogRepository.save(AiActionLog.record(
                store, store.getAccount(), AiActionType.DRAFT_CREATED,
                careType.name(), message.getAiGeneratedMessageId(), "고객 케어 초안 생성"));

        return AiGeneratedMessageResponseDto.from(message);
    }

    // ===================== 내부 집계 =====================

    private AiCustomerCareCardDto buildCard(Store store, AiCareType careType) {
        Long storeId = store.getStoreId();
        int targetCount = switch (careType) {
            case CART_INTEREST -> (int) Math.min(cartRepository.countByStore_StoreId(storeId), Integer.MAX_VALUE);
            case INACTIVE_REGULAR -> countInactiveRegulars(storeId);
            case INQUIRY_HESITATION -> (int) Math.min(inquiryRepository.countByStore_StoreIdAndStatus(storeId, InquiryStatus.PENDING), Integer.MAX_VALUE);
        };
        int excludedCount = (int) aiGeneratedMessageRepository.countByStore_StoreIdAndTypeAndStatusAndSentAtAfter(
                storeId, AiMessageType.CUSTOMER_CARE, AiMessageStatus.SENT, LocalDateTime.now().minusDays(7));

        AiText prepared = aiTextGenerator.customerCareMessage(careType, store.getName(), null);

        return AiCustomerCareCardDto.builder()
                .careType(careType)
                .title(careType.getTitle())
                .priority(careType.getPriority())
                .targetCustomerCount(targetCount)
                .reason(buildReason(careType, targetCount))
                .preparedMessage(prepared.content())
                .recentlyNotifiedExcludedCount(excludedCount)
                .sendable(targetCount > 0)
                .build();
    }

    // 단골(완료 주문 3건 이상) 중 최근 30일 주문이 없는 고객 수 — QueryDSL GROUP BY HAVING으로 N+1 제거
    private int countInactiveRegulars(Long storeId) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        return orderRepository.findInactiveRegularAccountIds(storeId, OrderStatus.COMPLETED, 3, threshold).size();
    }

    private String buildReason(AiCareType careType, int targetCount) {
        if (targetCount == 0) {
            return "아직 대상 고객이 없습니다. 데이터가 쌓이면 대상을 찾아드릴게요.";
        }
        return switch (careType) {
            case CART_INTEREST -> "장바구니에 상품을 담아둔 고객 " + targetCount + "명이 구매를 고민하고 있어요.";
            case INACTIVE_REGULAR -> "자주 방문하던 단골 " + targetCount + "명이 최근 30일간 방문하지 않았어요.";
            case INQUIRY_HESITATION -> "문의 후 답변을 기다리는 고객이 " + targetCount + "명 있어요.";
        };
    }
}
