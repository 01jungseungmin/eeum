package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.domain.ai.entity.AiConversionEvent;
import com.eeum.eeum.domain.ai.entity.AiMessageDelivery;
import com.eeum.eeum.domain.ai.enums.AiConversionType;
import com.eeum.eeum.domain.ai.enums.AiDeliveryStatus;
import com.eeum.eeum.domain.ai.repository.AiConversionEventRepository;
import com.eeum.eeum.domain.ai.repository.AiMessageDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 메시지 발송 후 전환(주문/예약) 추적.
 * 전환 인정 기준: 발송 후 7일 이내, 같은 메시지×같은 고객은 1회만, 발송 이전 행동은 제외.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConversionService {

    private static final int ATTRIBUTION_WINDOW_DAYS = 7;

    private final AiMessageDeliveryRepository aiMessageDeliveryRepository;
    private final AiConversionEventRepository aiConversionEventRepository;

    @Transactional
    public void recordConversion(
            Long accountId,
            Long storeId,
            AiConversionType conversionType,
            Long orderId,
            Long reservationId,
            LocalDateTime occurredAt
    ) {
        LocalDateTime windowStart = occurredAt.minusDays(ATTRIBUTION_WINDOW_DAYS);
        List<AiMessageDelivery> deliveries = aiMessageDeliveryRepository
                .findByTargetAccountIdAndStore_StoreIdAndStatusAndSentAtAfterOrderBySentAtDesc(
                        accountId, storeId, AiDeliveryStatus.SENT, windowStart);

        // 발송 이전에 발생한 행동은 전환으로 보지 않는다
        AiMessageDelivery attributed = deliveries.stream()
                .filter(delivery -> delivery.getSentAt() != null && !delivery.getSentAt().isAfter(occurredAt))
                .findFirst() // 가장 최근 발송 메시지에 귀속
                .orElse(null);
        if (attributed == null) {
            return;
        }

        Long messageId = attributed.getMessage().getAiGeneratedMessageId();
        if (aiConversionEventRepository.existsByMessage_AiGeneratedMessageIdAndAccountId(messageId, accountId)) {
            return; // 같은 메시지×같은 고객 중복 전환 방지
        }
        try {
            aiConversionEventRepository.save(AiConversionEvent.record(
                    attributed.getMessage(), attributed.getStore(), accountId,
                    conversionType, orderId, reservationId, occurredAt, ATTRIBUTION_WINDOW_DAYS));
            log.info("[AI-CONVERSION] 전환 기록: messageId={}, type={}, storeId={}",
                    messageId, conversionType, storeId);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 unique 충돌 — 이미 기록된 것이므로 무시 (멱등)
            log.debug("[AI-CONVERSION] 중복 전환 무시: messageId={}, accountId={}", messageId, accountId);
        }
    }
}
