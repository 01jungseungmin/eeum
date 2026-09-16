package com.eeum.eeum.domain.ai.event;

import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageType;

import java.time.LocalDateTime;

// 1차 MVP: 실제 외부 발송 대신 이벤트만 발행 — 리스너는 로그 기록 수준으로 유지
public record AiMessageSentEvent(
        Long messageId,
        Long storeId,
        AiMessageType type,
        AiChannel channel,
        boolean scheduled,
        LocalDateTime occurredAt
) {
}
