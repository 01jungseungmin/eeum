package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiDeliveryStatus;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// AI 메시지 수신자별 발송 결과 — 일부 실패가 전체 실패로 이어지지 않도록 개별 기록
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_message_delivery", indexes = {
        @Index(name = "idx_ai_delivery_message", columnList = "ai_generated_message_id"),
        @Index(name = "idx_ai_delivery_target_store", columnList = "target_account_id, store_id, sent_at")
})
public class AiMessageDelivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_message_delivery_id")
    private Long aiMessageDeliveryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_generated_message_id", nullable = false)
    private AiGeneratedMessage message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    // 수신 고객 accountId — NO_TARGET 기록은 null
    @Column(name = "target_account_id")
    private Long targetAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private AiChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AiDeliveryStatus status;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    // 실패 사유 — 개인정보(토큰/전화번호 원문)는 넣지 않는다
    @Column(name = "failed_reason", length = 200)
    private String failedReason;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    public static AiMessageDelivery record(
            AiGeneratedMessage message,
            Store store,
            Long targetAccountId,
            AiChannel channel,
            AiDeliveryStatus status,
            String failedReason,
            String providerMessageId
    ) {
        AiMessageDelivery delivery = new AiMessageDelivery();
        delivery.message = message;
        delivery.store = store;
        delivery.targetAccountId = targetAccountId;
        delivery.channel = channel;
        delivery.status = status;
        delivery.failedReason = failedReason;
        delivery.providerMessageId = providerMessageId;
        delivery.sentAt = status == AiDeliveryStatus.SENT ? LocalDateTime.now() : null;
        return delivery;
    }
}
