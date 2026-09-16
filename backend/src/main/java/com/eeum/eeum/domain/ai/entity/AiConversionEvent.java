package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.ai.enums.AiConversionType;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// AI 메시지 발송 후 전환(주문/예약) 기록 — 같은 메시지×같은 고객은 1회만 인정 (unique)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_conversion_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_conversion_message_account",
                columnNames = {"ai_generated_message_id", "account_id"}),
        indexes = @Index(name = "idx_ai_conversion_store", columnList = "store_id, converted_at"))
public class AiConversionEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_conversion_event_id")
    private Long aiConversionEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_generated_message_id", nullable = false)
    private AiGeneratedMessage message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversion_type", nullable = false, length = 20)
    private AiConversionType conversionType;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "converted_at", nullable = false)
    private LocalDateTime convertedAt;

    @Column(name = "attribution_window_days", nullable = false)
    private int attributionWindowDays;

    public static AiConversionEvent record(
            AiGeneratedMessage message,
            Store store,
            Long accountId,
            AiConversionType conversionType,
            Long orderId,
            Long reservationId,
            LocalDateTime convertedAt,
            int attributionWindowDays
    ) {
        AiConversionEvent event = new AiConversionEvent();
        event.message = message;
        event.store = store;
        event.accountId = accountId;
        event.conversionType = conversionType;
        event.orderId = orderId;
        event.reservationId = reservationId;
        event.convertedAt = convertedAt;
        event.attributionWindowDays = attributionWindowDays;
        return event;
    }
}
